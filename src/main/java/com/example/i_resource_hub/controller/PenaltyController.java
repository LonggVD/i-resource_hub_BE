package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.request.PenaltyRequest;
import com.example.i_resource_hub.dto.response.PenaltyResponse;
import com.example.i_resource_hub.service.PenaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/penalties")
@RequiredArgsConstructor
@Tag(name = "Penalty", description = "Quản lý xử phạt sinh viên")
public class PenaltyController {

    private final PenaltyService penaltyService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('RESOURCE_MANAGE', 'ADMIN')")
    @Operation(summary = "Tạo án phạt mới")
    public ResponseEntity<PenaltyResponse> createPenalty(@Valid @RequestBody PenaltyRequest request,
                                                          Authentication authentication) {
        String adminUserId = authentication.getName();
        return ResponseEntity.ok(penaltyService.createPenalty(request, adminUserId));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('RESOURCE_MANAGE', 'ADMIN')")
    @Operation(summary = "Lấy danh sách tất cả án phạt")
    public ResponseEntity<List<PenaltyResponse>> getAllPenalties() {
        return ResponseEntity.ok(penaltyService.getAllPenalties());
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Lấy án phạt theo userId")
    public ResponseEntity<List<PenaltyResponse>> getPenaltiesByUser(@PathVariable String userId) {
        return ResponseEntity.ok(penaltyService.getPenaltiesByUser(userId));
    }

    @GetMapping("/{penaltyId}")
    @Operation(summary = "Lấy chi tiết án phạt")
    public ResponseEntity<PenaltyResponse> getPenaltyById(@PathVariable String penaltyId) {
        return ResponseEntity.ok(penaltyService.getPenaltyById(penaltyId));
    }

    @PatchMapping("/{penaltyId}/revoke")
    @PreAuthorize("hasAnyAuthority('RESOURCE_MANAGE', 'ADMIN')")
    @Operation(summary = "Thu hồi (ân xá) án phạt")
    public ResponseEntity<PenaltyResponse> revokePenalty(@PathVariable String penaltyId) {
        return ResponseEntity.ok(penaltyService.revokePenalty(penaltyId));
    }

    @DeleteMapping("/{penaltyId}")
    @PreAuthorize("hasAnyAuthority('RESOURCE_MANAGE', 'ADMIN')")
    @Operation(summary = "Xóa mềm án phạt")
    public ResponseEntity<Void> deletePenalty(@PathVariable String penaltyId) {
        penaltyService.deletePenalty(penaltyId);
        return ResponseEntity.noContent().build();
    }
}
