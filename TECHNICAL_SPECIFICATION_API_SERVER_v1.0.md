# ТЗ v1.0: MVP сервера авторизации и аутентификации (Java)

Версия документа: `v1.0`

## 1. Цель проекта
Разработать REST API-сервер с MVP-функционалом регистрации, аутентификации и авторизации пользователей на базе JWT, с возможностью дальнейшего расширения бизнес-функций.

## 2. Технологический стек
- Язык: `Java 21`
- Framework: `Spring Boot 3.x`
- Сборка: `Maven`
- БД: `MySQL 8.0+`
- Миграции: `Flyway`
- Безопасность: `Spring Security`, `JWT` (access + refresh token)
- Хеширование паролей: `BCrypt`
- Документация API: `OpenAPI/Swagger`
- Контейнеризация: `Docker`, `docker-compose`
- Тестирование: `JUnit 5`, `Spring Boot Test`, `Testcontainers` (опционально)

## 3. Функциональные требования

### 3.1 Основные сущности
Для MVP реализовать сущность `User`:
- `id` (UUID, PK)
- `email` (string, обязательное, уникальное, формат email)
- `passwordHash` (string, обязательное; хранить только в виде хеша)
- `role` (enum: `USER`, `MANAGER`, `EMPLOYEE`, `ADMIN`)
- `isActive` (boolean, default `true`)
- `isDeleted` (boolean, default `false`; признак мягкого удаления)
- `deletedAt` (timestamp, nullable)
- `emailVerified` (boolean, default `false`; `true` только после успешной верификации)
- `emailVerifiedAt` (timestamp, nullable)
- `createdAt` (timestamp)
- `updatedAt` (timestamp)

Дополнительно (связанные сущности для токенов):
- `EmailVerificationToken` — одноразовый токен подтверждения email (в БД хранить только хэш, см. раздел 8)
- `RefreshToken` — refresh-сессия пользователя (в БД хранить только хэш токена, срок жизни и признак отзыва, см. раздел 8)

### 3.2 API-эндпоинты (v1)
Базовый префикс: `/api/v1`

- `POST /auth/register` - регистрация пользователя
- `POST /auth/login` - аутентификация пользователя
- `POST /auth/verify-email` - подтверждение email по токену верификации
- `POST /auth/resend-verification` - повторная отправка письма подтверждения email
- `POST /auth/logout` - выход пользователя (инвалидация текущей сессии/токена)
- `GET /auth/me` - профиль текущего пользователя (требуется JWT)
- `POST /auth/refresh` - обновление access token (refresh token обязателен)
- `DELETE /users/{id}` - мягкое удаление пользователя (ADMIN)
- Проверка состояния: **`/actuator/health`** (Spring Boot Actuator; **без** префикса `/api/v1`) или отдельный `GET /health` на корне, если настроен явно — в Swagger указывается фактический путь

Правила ответов для `DELETE /users/{id}`:
- Пользователь найден и помечен как удаленный -> `204 No Content`
- Пользователь уже был удален -> `204 No Content` (идемпотентно)
- Пользователь не найден -> `404 Not Found`

### 3.3 Права доступа
- Публичные endpoints: `POST /auth/register`, `POST /auth/login`, `POST /auth/verify-email`, `POST /auth/resend-verification`, healthcheck (вне `/api/v1`, если используется actuator)
- Защищенные endpoints: `POST /auth/logout`, `GET /auth/me`, `DELETE /users/{id}`, внутренние API
- Доступ по ролям:
  - `USER`: доступ к собственному профилю
  - `EMPLOYEE`: базовый доступ к внутренним endpoints (на следующем этапе)
  - `MANAGER`: расширенный доступ к управленческим endpoints (на следующем этапе)
  - `ADMIN`: полный доступ к административным endpoint
- `DELETE /users/{id}`: только роль `ADMIN`; иначе `403 Forbidden` (`code`: `FORBIDDEN`)

## 4. Нефункциональные требования

### 4.1 Производительность
- Среднее время ответа: до 300 мс для типовых auth-запросов при локальной нагрузке (до 100 RPS)
- Поддержка конкурентной обработки запросов без потери консистентности
- Rate limiting (MVP): лимиты задаются через переменные окружения (см. раздел 10.1), значения по умолчанию:
  - `POST /auth/login`: не более `RATE_LIMIT_LOGIN_MAX_ATTEMPTS` неудачных попыток на связку IP+email за окно `RATE_LIMIT_LOGIN_WINDOW_SECONDS`
  - `POST /auth/resend-verification`: не чаще одного запроса на `email` за `RATE_LIMIT_RESEND_VERIFICATION_SECONDS`
  - `POST /auth/refresh`: не более `RATE_LIMIT_REFRESH_MAX_REQUESTS` запросов с одного IP за окно `RATE_LIMIT_REFRESH_WINDOW_SECONDS` (защита от брутфорса)

### 4.2 Надежность
- Гарантированная валидация входных данных
- Централизованная обработка ошибок
- Корректные HTTP-статусы и единый формат ошибок

### 4.3 Безопасность (MVP)
- Авторизация через Bearer JWT access token
- Использование refresh token обязательно для обновления access token
- Хранение пароля только в виде `BCrypt` хеша (без plaintext)
- Защита приватных endpoints через Spring Security
- Ролевая модель: `USER`, `MANAGER`, `EMPLOYEE`, `ADMIN` (в MVP можно ограничиться `USER` и `EMPLOYEE`)
- Защита от SQL injection/XSS за счет стандартных практик Spring/JPA
- Для пользователей с `isDeleted=true` доступ к auth-flow запрещен: `login`, `refresh`, `me` возвращают `403 Forbidden`
- Проверка `isDeleted` выполняется на каждом защищенном запросе; даже при валидном access token ответ должен быть `403 Forbidden`
- Для пользователей с `isActive=false` (и не удаленных) вход и обновление сессии запрещены: `login` и `refresh` возвращают `403 Forbidden` (`code`: `ACCOUNT_DISABLED`); защищенные endpoints — `403 Forbidden`

Параметры токенов и JWT (MVP):
- Алгоритм подписи access token: `HS256`, секрет `JWT_SECRET` (длина ключа достаточная для HMAC-SHA256)
- В access JWT обязательные claims: `sub` (user id), `sessionId`, `exp`, `iat`; опционально `iss` и `aud`, если заданы `JWT_ISSUER` и `JWT_AUDIENCE` в окружении
- Формат **refresh token** снаружи: непрозрачная случайная строка (не JWT); в БД хранится только хеш (как уже указано для `refresh_tokens.token_hash`)

Параметры токенов:
- `access token` - 15 минут (`900` секунд); в payload обязателен claim `sessionId` (UUID строки `refresh_tokens`)
- `refresh token` - 7 дней (`604800` секунд)

Политика refresh token (MVP):
- При каждом успешном `POST /auth/refresh` выдается **новая пара** access + refresh (rotation): предыдущий refresh помечается как отозванный или заменяется новой записью с явной связью `replaced_by` (на усмотрение реализации, но поведение для клиента — всегда использовать последний refresh).
- При попытке использовать **уже отозванный** или **не последний** refresh (возможная утечка/повторное использование) — ответ `401 Unauthorized`, **инвалидация всех refresh-сессий** данного пользователя (reuse detection), аудит-событие уровня WARN без логирования самого токена.
- `POST /auth/logout` с валидным access token отзывает **текущую** refresh-сессию (ту, что связана с запросом); опционально в будущем — «logout all devices».

Связь access JWT и refresh-сессии (обязательно для `logout`):
- В access JWT включается claim `sessionId` (строка UUID), совпадающий с `id` строки в таблице `refresh_tokens`, выданной при последнем успешном `login` или `refresh`.
- При rotation refresh: выдаётся новая строка `refresh_tokens` с новым `id`; в новом access token поле `sessionId` обновляется на этот `id`.
- `POST /auth/logout`: по `sessionId` из access token находится запись refresh-сессии и помечается отозванной (`revoked_at`); при отсутствии записи или несовпадении — `401 Unauthorized`.

### 4.4 Логирование и мониторинг
- Логи строго в формате `JSON` (уровни: INFO/WARN/ERROR)
- Корреляционный `requestId` в логах
- Health endpoint обязателен
- Метрики через actuator (по возможности)
- Запрещено логировать пароли, токены и чувствительные поля
- Для `POST /auth/verify-email`: токен передается только в теле запроса, не в URL

## 5. Контракты API

### 5.1 Формат успешного ответа
`application/json`

Регистрация (`POST /auth/register`):
- Тело запроса: `email`, `password` (роль **не** принимается от клиента; при создании пользователя выставляется `role=USER` на сервере)
- `email` перед сохранением нормализуется: обрезка пробелов, приведение домена к нижнему регистру по правилам RFC (практически: весь email в lower case для сравнения и хранения)
- Ответ `201 Created`: тело — как минимум идентификатор и email пользователя без `passwordHash` (детальный состав — в Swagger; опционально дублирует поля из примера login `user`)

Пример тела запроса (`POST /auth/register`):
```json
{
  "email": "user@example.com",
  "password": "Secret123"
}
```

Пример (`POST /auth/login`):
```json
{
  "accessToken": "<jwt-token>",
  "refreshToken": "<refresh-token>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "6f2f19d3-3e41-4f81-b8cb-5f0df2f9fdf6",
    "email": "user@example.com",
    "role": "USER"
  }
}
```

Пример (`POST /auth/logout`):
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

Пример (`POST /auth/verify-email`):
```json
{
  "token": "<verification-token>"
}
```

Успешный ответ:
```json
{
  "success": true,
  "message": "Email verified successfully"
}
```

Пример (`POST /auth/resend-verification`):
```json
{
  "email": "user@example.com"
}
```

Ответ:
```json
{
  "success": true,
  "message": "Verification email sent"
}
```

Пример (`POST /auth/refresh`):
```json
{
  "refreshToken": "<refresh-token>"
}
```

Успешный ответ — тот же формат, что и у `POST /auth/login` (новая пара `accessToken` + `refreshToken`, `expiresIn` для access).

Поведение `POST /auth/logout` в MVP:
- Сервер инвалидирует refresh token (например, через denylist/хранилище токенов) и завершает сессию пользователя.
- Клиент удаляет access token на своей стороне после успешного logout.
- Запрос без/с невалидным токеном: `401 Unauthorized`.

Поведение мягкого удаления пользователя (`DELETE /users/{id}`):
- Физическое удаление записи из `users` не выполняется.
- Сервер выставляет `isDeleted=true`, `deletedAt=now()`, `isActive=false`.
- Все refresh-токены удаленного пользователя немедленно отзываются.
- Удаленный пользователь не может входить и обновлять сессию (`403 Forbidden`).
- Запрос `GET /auth/me` для удаленного пользователя возвращает `403 Forbidden`, даже если access token еще не истек.
- Операция идемпотентна: повторный запрос для уже удаленного пользователя возвращает `204 No Content`.

Поведение email verification:
- После `register` создается токен верификации email с ограниченным TTL (рекомендуемо: 24 часа; задается конфигом `EMAIL_VERIFICATION_EXPIRES_SECONDS`).
- Отправка письма верификации: при ошибке SMTP после успешного сохранения пользователя и токена в БД ответ остаётся `201 Created` (тело как при успехе); ошибка фиксируется в логах уровня `ERROR` с `requestId`, без утечки деталей SMTP клиенту; пользователь может вызвать `POST /auth/resend-verification`.
- При ошибке SMTP на `POST /auth/resend-verification` возвращать `503 Service Unavailable` в едином формате ошибки (клиент может повторить запрос позже).
- Анти-перечисление email: при `POST /auth/resend-verification` для **несуществующего** email или уже **подтвержденного** email ответ всегда `200 OK` с тем же телом, что и при успешной отправке (`success`, `message`), письмо не отправляется; это снижает утечку факта регистрации адреса
- До подтверждения email вход в систему запрещен: `POST /auth/login` при верных учетных данных, но `emailVerified=false` — `403 Forbidden` (без выдачи токенов).
- `POST /auth/verify-email` активирует учетную запись при валидном токене.
- Коды ошибок (единая семантика):
  - отсутствует поле `token` в теле запроса или оно пустое — `400 Bad Request`
  - просроченный, невалидный, уже использованный токен — `400 Bad Request`
  - `401 Unauthorized` для этого endpoint не используется (нет контекста аутентификации пользователя)

### 5.2 Формат ошибки (единый)
Поле `message` предназначено для человека; клиентская логика не должна опираться на его точный текст. Для стабильной обработки на клиенте используется опциональное поле `code` (строка, enum на стороне сервера), например `EMAIL_NOT_VERIFIED`, `USER_DELETED`, `RATE_LIMIT_EXCEEDED`.

```json
{
  "timestamp": "2026-04-09T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid credentials",
  "path": "/api/v1/auth/login"
}
```

### 5.2.1 Справочник `code` в ошибках (MVP)
Минимальный набор стабильных значений `code` (расширяется по мере необходимости):

| `code` | Типичный HTTP | Когда |
|--------|----------------|--------|
| `VALIDATION_ERROR` | 400 | Ошибка валидации полей |
| `INVALID_TOKEN` | 400 | Невалидный/просроченный токен верификации |
| `INVALID_CREDENTIALS` | 401 | Неверный логин/пароль |
| `UNAUTHORIZED` | 401 | Нет или просрочен access token |
| `INVALID_SESSION` | 401 | Нет refresh-сессии / неверный `sessionId` при logout |
| `EMAIL_NOT_VERIFIED` | 403 | Вход при неподтвержденном email |
| `USER_DELETED` | 403 | Удаленный пользователь |
| `ACCOUNT_DISABLED` | 403 | `isActive=false` |
| `FORBIDDEN` | 403 | Недостаточно прав (не ADMIN на `DELETE /users`) |
| `CONFLICT` | 409 | Email уже занят активным пользователем |
| `RATE_LIMIT_EXCEEDED` | 429 | Превышен rate limit |
| `EMAIL_DELIVERY_FAILED` | 503 | Ошибка SMTP на `resend-verification` |

### 5.3 HTTP status matrix
- `POST /auth/register`: `201 Created`, `400 Bad Request`, `409 Conflict`, `429 Too Many Requests`
- `POST /auth/login`: `200 OK`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden` (email не подтвержден или пользователь удален), `429 Too Many Requests`
- `POST /auth/verify-email`: `200 OK`, `400 Bad Request`
- `POST /auth/resend-verification`: `200 OK` (единый ответ для неподтвержденного и подтвержденного пользователя), `400 Bad Request`, `429 Too Many Requests`, `503 Service Unavailable` (ошибка доставки письма / SMTP)
- `POST /auth/refresh`: `200 OK`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden` (пользователь удален), `429 Too Many Requests`
- `POST /auth/logout`: `200 OK`, `401 Unauthorized`
- `GET /auth/me`: `200 OK`, `401 Unauthorized`, `403 Forbidden` (пользователь удален)
- `DELETE /users/{id}`: `204 No Content`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`

## 6. Валидация данных
- `email`: обязательно, валидный формат, уникальный среди активных пользователей; нормализация при сохранении (см. раздел 5.1)
- `email`: обязательное подтверждение через верификацию (ссылка/код) перед активацией учетной записи
- `password`: обязательно, минимум 8 символов, минимум 1 буква и 1 цифра
- `token` (в теле `POST /auth/verify-email`): обязательное непустое значение, одноразовый секрет верификации, ограниченный по времени (см. `EMAIL_VERIFICATION_EXPIRES_SECONDS`)
- `role`: в MVP при `POST /auth/register` **не передается клиентом**; сервер назначает `USER`. Смена ролей — отдельный админ-процесс вне MVP или вручную в БД
- `userId` в `DELETE /users/{id}`: обязательный UUID существующего пользователя
- При ошибках валидации возвращать `400 Bad Request` с деталями

## 7. Архитектура и структура кода
Слои:
- `controller` - HTTP-слой
- `service` - бизнес-логика
- `repository` - доступ к БД
- `dto` - входные/выходные модели
- `exception` - глобальный обработчик ошибок
- `security` - JWT-фильтр, конфигурация Spring Security

Принципы:
- Не возвращать entity напрямую наружу
- Использовать DTO + mapper
- Конфигурация по профилям: `dev`, `test`, `prod`
- JWT-проверка через security filter/middleware

## 8. База данных
- Таблица `users`:
  - поля сущности `User` из раздела 3.1 (`email_verified`, `email_verified_at`, `is_deleted`, `deleted_at` обязательны)
  - уникальность email должна применяться только к не удаленным пользователям.
  - для MySQL 8 рекомендуется стратегия с вычисляемым столбцом (например, `active_email = IF(is_deleted = 0, email, NULL)`) и уникальным индексом по `active_email`; это гарантирует уникальность среди активных пользователей и допускает пере-регистрацию после soft delete
- Таблица `email_verification_tokens` (или эквивалентное имя):
  - `id` (UUID, PK)
  - `user_id` (FK -> `users.id`)
  - `token_hash` (string, обязательное; хранить только хэш, например SHA-256 от случайного секрета)
  - `expires_at` (timestamp)
  - `used_at` (timestamp, nullable; заполняется при успешной верификации)
  - `created_at` (timestamp)
  - Индексы: по `user_id`, по `expires_at` (для очистки просроченных)
- Таблица `refresh_tokens`:
  - `id` (UUID, PK)
  - `user_id` (FK -> `users.id`)
  - `token_hash` (string, обязательное)
  - `expires_at` (timestamp)
  - `revoked_at` (timestamp, nullable)
  - `replaced_by_token_id` (UUID, nullable, FK на `refresh_tokens.id` — для rotation)
  - `created_at` (timestamp)
  - Индексы: по `user_id`, по `expires_at`, по `token_hash` (уникальный среди неотозванных — по политике БД)
- Очистка просроченных и отозванных записей (MVP обязательно): фоновая задача по расписанию (например, раз в сутки, cron через Spring `@Scheduled` или эквивалент) **физически удаляет** строки:
  - `email_verification_tokens`: где `expires_at` старше `TOKEN_CLEANUP_RETENTION_DAYS` суток от текущего момента **или** `used_at` заполнен и прошло столько же суток с `used_at`
  - `refresh_tokens`: где `expires_at` в прошлом **или** `revoked_at` заполнен, и с момента `expires_at`/`revoked_at` прошло не менее `TOKEN_CLEANUP_RETENTION_DAYS` суток
- Все изменения схемы - только через миграции Flyway

## 9. Тестирование

### 9.1 Обязательные тесты
- Unit-тесты для auth service (register/login/token validation)
- Интеграционные тесты auth-контроллера (`MockMvc`/`WebTestClient`)
- Тесты валидации и error handling
- Тесты доступа к защищенным endpoints (401/403)
- Тесты `logout`: успешный выход (`200`), выход без токена (`401`), повторный refresh после logout (`401`, если используются refresh token)
- Тесты email verification: вход до подтверждения email — `403 Forbidden` (тело/сообщение: email не подтвержден), после подтверждения — успешен; невалидный/просроченный verify token — `400 Bad Request`
- Тесты `refresh`: успешный refresh возвращает новую пару токенов; повторное использование старого refresh после rotation — `401` и инвалидация сессий пользователя (reuse)
- Тесты `resend-verification`: повторная отправка для неподтвержденного пользователя - `200`, для подтвержденного - идемпотентный `200`; для несуществующего email — `200` (без отправки письма, анти-перечисление)
- Тесты `isActive=false`: `login`/`refresh` — `403` (`ACCOUNT_DISABLED`)
- Тесты soft delete: удаление пользователя (ADMIN) выставляет `isDeleted`; повторное удаление — `204`; login/refresh/me для удаленного пользователя — `403`; refresh-токены отозваны
- Тесты soft delete: удаление несуществующего `userId` — `404 Not Found`
- Тесты rate limiting: превышение лимита для `login`/`resend-verification`/`refresh` возвращает `429 Too Many Requests`
- Тесты доставки email: ошибка SMTP при `register` — по-прежнему `201`; ошибка SMTP при `resend-verification` — `503`
- Тесты `logout`: при несовпадении `sessionId` в JWT с активной refresh-сессией — `401`

### 9.2 Критерии покрытия
- Минимум 70% по ключевым пакетам (`service`, `controller`, `security`)

## 10. DevOps и запуск

### 10.1 Локальный запуск
- `docker-compose` поднимает:
  - `app`
  - `mysql`
- Конфиги через переменные окружения:
  - `DB_URL`
  - `DB_USER`
  - `DB_PASSWORD`
  - `SERVER_PORT`
  - `JWT_SECRET`
  - `JWT_ISSUER` (опционально; если задан — проверка `iss` при валидации access token)
  - `JWT_AUDIENCE` (опционально; если задан — проверка `aud`)
  - `JWT_EXPIRES_SECONDS` (TTL access token в секундах; ожидаемое значение `900`)
  - `JWT_REFRESH_EXPIRES_SECONDS` (TTL refresh token; ожидаемое значение `604800`)
  - `EMAIL_VERIFICATION_EXPIRES_SECONDS` (TTL токена верификации; рекомендуемо `86400` — 24 ч)
  - `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `MAIL_FROM` (отправка писем верификации)
  - `RATE_LIMIT_LOGIN_MAX_ATTEMPTS` (по умолчанию `5`)
  - `RATE_LIMIT_LOGIN_WINDOW_SECONDS` (по умолчанию `900` — 15 минут)
  - `RATE_LIMIT_RESEND_VERIFICATION_SECONDS` (по умолчанию `60`)
  - `RATE_LIMIT_REFRESH_MAX_REQUESTS` (по умолчанию `30`)
  - `RATE_LIMIT_REFRESH_WINDOW_SECONDS` (по умолчанию `60`)
  - `TOKEN_CLEANUP_RETENTION_DAYS` (по умолчанию `30` — хранить метаданные отозванных/просроченных токенов не дольше перед удалением job'ом)

### 10.2 CI (желательно)
- Этапы: build -> test -> package
- Проверка форматирования/линтинга (если подключено)

## 11. Открытые решения (нужно утвердить до старта)
Открытых решений нет.

Принятые решения:
- `refresh token` в MVP обязателен
- Срок жизни `access token`: 15 минут
- Срок жизни `refresh token`: 7 дней
- Подтверждение `email` в MVP обязательно
- Формат логов: `JSON`
- Поле верификации в теле запроса: `token` (`POST /auth/verify-email`)
- Связь access JWT с сессией: claim `sessionId` = `refresh_tokens.id`

## 12. Критерии приемки
- Все auth-endpoints из раздела 3 реализованы и задокументированы в Swagger
- Миграции применяются автоматически при старте
- Валидация и единый формат ошибок работают
- Логин возвращает валидные `access/refresh` токены, `logout` выполняется корректно, защищенные endpoints требуют токен
- Логин доступен только после подтверждения `email`
- `POST /auth/verify-email` и `POST /auth/resend-verification` реализованы и задокументированы в Swagger
- `DELETE /users/{id}` (soft delete) реализован и задокументирован в Swagger
- Тесты проходят (`mvn test`)
- Сервис запускается через `docker-compose up`
- Healthcheck возвращает `UP`
- Логи приложения в формате `JSON`; чувствительные поля запросов не попадают в логи
- Миграции покрывают таблицы `users`, `email_verification_tokens`, `refresh_tokens`
- Миграции покрывают поля soft delete в `users` и индексы для корректной уникальности email
- Реализованы scheduled cleanup токенов и настройки `TOKEN_CLEANUP_RETENTION_DAYS`
- Ошибки API при необходимости содержат стабильное поле `code` (см. раздел 5.2 и справочник 5.2.1)
- В Swagger описаны путь health/actuator, claims access JWT (`sessionId`, обязательные поля) и правило anti-enumeration для `resend-verification`

## 13. Оценка сроков (MVP)
- Проектный каркас + Spring Security + БД + миграции: 1 день
- Register/Login/Verify/Refresh/Logout + soft delete + rate limiting + обработка ошибок: 2-3 дня
- Тесты + Swagger + docker-compose: 1-1.5 дня
- Итого: **4-5.5 дня**
