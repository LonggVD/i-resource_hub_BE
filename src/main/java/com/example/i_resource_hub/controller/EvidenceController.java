package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.request.EvidenceRequest;
import com.example.i_resource_hub.dto.response.EvidenceResponse;
import com.example.i_resource_hub.service.EvidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evidences")
@RequiredArgsConstructor
@Tag(name = "Booking Evidence", description = "Quản lý hình ảnh minh chứng mượn/trả")
public class EvidenceController {

    private final EvidenceService evidenceService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('RESOURCE_MANAGE', 'ADMIN')")
    @Operation(summary = "Thêm ảnh minh chứng (lúc giao/nhận hoặc báo sự cố)")
    public ResponseEntity<EvidenceResponse> addEvidence(@RequestBody EvidenceRequest request, Authentication authentication) {
        return ResponseEntity.ok(evidenceService.addEvidence(request, authentication.getName()));
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Lấy danh sách ảnh minh chứng của 1 đơn mượn")
    public ResponseEntity<List<EvidenceResponse>> getEvidencesByBooking(@PathVariable String bookingId) {
        return ResponseEntity.ok(evidenceService.getEvidencesByBooking(bookingId));
    }

    @GetMapping("/bookings")
    @Operation(summary = "Lấy danh sách ảnh minh chứng của nhiều đơn mượn")
    public ResponseEntity<List<EvidenceResponse>> getEvidencesByBookings(@RequestParam List<String> bookingIds) {
        return ResponseEntity.ok(evidenceService.getEvidencesByBookings(bookingIds));
    }
}
