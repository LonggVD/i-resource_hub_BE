package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.request.CreditScoreRequest;
import com.example.i_resource_hub.dto.request.UserRequest;
import com.example.i_resource_hub.dto.response.UserResponse;
import com.example.i_resource_hub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<Page<UserResponse>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String unitId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String roleId,
            Pageable pageable) {
        return ResponseEntity.ok(userService.getPageUsers(keyword, unitId, status, roleId, pageable));
    }

    @GetMapping("/students")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE', 'RESOURCE_MANAGE', 'ADMIN')")
    public ResponseEntity<Page<UserResponse>> getStudents(
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return ResponseEntity.ok(userService.getStudents(keyword, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.createUser(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable String id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<UserResponse> toggleStatus(@PathVariable String id) {
        return ResponseEntity.ok(userService.toggleStatus(id));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<UserResponse> approveUser(@PathVariable String id) {
        return ResponseEntity.ok(userService.approveUser(id));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<UserResponse> rejectUser(@PathVariable String id) {
        return ResponseEntity.ok(userService.rejectUser(id));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable String id) {
        String newPassword = userService.resetPassword(id);
        return ResponseEntity.ok(Map.of("newPassword", newPassword));
    }

    @PatchMapping("/{id}/credit-score")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<UserResponse> updateCreditScore(@PathVariable String id,
            @Valid @RequestBody CreditScoreRequest request) {
        return ResponseEntity.ok(userService.updateCreditScore(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
