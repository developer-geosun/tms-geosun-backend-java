# tms-geosun-backend-java

MVP backend for authentication and authorization based on Java 21 + Spring Boot 3.

## Local run (without Docker)

1. Ensure MySQL 8 is running.
2. Copy `.env.example` values into your environment.
3. Run:

```bash
mvn spring-boot:run
```

## Local run (Docker Compose)

```bash
docker compose up --build
```

## Useful endpoints

- Health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
