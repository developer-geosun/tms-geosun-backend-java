package com.geosun.tms.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3: опис API, Bearer JWT та примітки з ТЗ (sessionId, health, anti-enumeration).
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        String description = """
                MVP authentication API (v1). Access JWT: HS256, mandatory claims `sub` (user id), \
                `sessionId` (id of row in `refresh_tokens`), `iat`, `exp`; optional `iss` / `aud` if configured.

                Health (no API prefix): `GET /actuator/health`.

                `POST /auth/resend-verification` uses anti-enumeration: identical 200 response for unknown \
                or already verified emails (no email sent).
                """;

        return new OpenAPI()
                .info(new Info()
                        .title("TMS GeoSun Auth API")
                        .version("v1")
                        .description(description))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token; must match an active refresh session (`sessionId`).")));
    }
}
