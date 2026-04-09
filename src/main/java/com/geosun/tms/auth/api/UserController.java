package com.geosun.tms.auth.api;

import com.geosun.tms.auth.service.UserDeletionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Адміністративні операції над користувачами.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserDeletionService userDeletionService;

    public UserController(UserDeletionService userDeletionService) {
        this.userDeletionService = userDeletionService;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable("id") String id) {
        userDeletionService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
