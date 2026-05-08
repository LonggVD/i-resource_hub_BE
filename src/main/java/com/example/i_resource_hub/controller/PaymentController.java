package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.response.PaymentLinkResponse;
import com.example.i_resource_hub.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Management", description = "Quản lý thanh toán PayOS")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/penalty/{penaltyId}")
    @Operation(summary = "Tạo link thanh toán cho án phạt")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentLinkResponse> createPenaltyPayment(@PathVariable String penaltyId) {
        try {
            return ResponseEntity.ok(paymentService.createPaymentLink(penaltyId));
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo link thanh toán: " + e.getMessage());
        }
    }

    @PostMapping("/verify/{orderCode}")
    @Operation(summary = "Xác nhận trạng thái thanh toán từ PayOS")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> verifyPayment(@PathVariable long orderCode) {
        try {
            paymentService.verifyPayment(orderCode);
            return ResponseEntity.ok("Thanh toán thành công");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Xác nhận thất bại: " + e.getMessage());
        }
    }
}
