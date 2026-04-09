package com.geosun.tms.auth.api;

import com.geosun.tms.auth.config.OpenApiConfig;
import com.geosun.tms.auth.service.UserDeletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Адміністративні операції над користувачами.
 */
@Tag(name = "Users (admin)")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserDeletionService userDeletionService;

    public UserController(UserDeletionService userDeletionService) {
        this.userDeletionService = userDeletionService;
    }

    @Operation(summary = "Soft-delete user", description = "ADMIN only; idempotent 204.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable("id") String id) {
        userDeletionService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
