# tms-geosun-backend-java

MVP backend для аутентификации и авторизации на базе Java 21 + Spring Boot 3.

## Локальный запуск (без Docker)

1. Убедитесь, что запущен MySQL 8.
2. Скопируйте значения из `.env.example` в переменные окружения.
3. Запустите:

```bash
mvn spring-boot:run
```

## Локальный запуск (Docker Compose)

```bash
docker compose up --build
```

## Полезные endpoints

- Health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## Использование Postman-коллекции

В репозитории есть готовые файлы для тестирования потока аутентификации:

- Коллекция: `postman_collection_auth.json`
- Окружение: `postman_environment_local.json`

Как запустить:

1. Запустите backend локально (например, `mvn spring-boot:run`).
2. В Postman импортируйте оба файла из корня репозитория.
3. Выберите окружение `tms-geosun-backend-java local`.
4. При необходимости измените `baseUrl`, `testEmail` и `testPassword`.
5. Выполняйте запросы по порядку:
   - `01 - Health`
   - `02 - Register`
   - `03 - Resend verification`
   - `04 - Verify email (manual token)` (подставьте `verifyToken` из email/логов)
   - `05 - Login`
   - `06 - Me`
   - `07 - Refresh`
   - `08 - Logout`

Примечания:

- `05 - Login` и `07 - Refresh` автоматически обновляют `accessToken` и `refreshToken` в выбранном окружении.
- `06 - Me` и `08 - Logout` используют Bearer-авторизацию с `{{accessToken}}`.
- Если регистрация не проходит из-за уже существующего пользователя, измените `testEmail` и снова начните с `02 - Register`.

## Тесты и покрытие

- Запустить все тесты: `mvn test`
- Запустить тесты и проверить порог покрытия JaCoCo (минимум 55% по bundle): `mvn verify`
- HTML-отчет после тестов: `target/site/jacoco/index.html`

Интеграционные тесты используют профиль `test` (H2 in-memory, Flyway выключен, `JavaMailSender` замокан). В этом профиле отключен mail health для Actuator, чтобы мок не ломал запуск приложения.
