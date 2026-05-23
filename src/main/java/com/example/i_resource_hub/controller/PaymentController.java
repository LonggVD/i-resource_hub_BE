package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.response.PaymentLinkResponse;
import com.example.i_resource_hub.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
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

    /**
     * PayOS sẽ POST tới đây sau khi giao dịch hoàn tất. Endpoint phải PUBLIC vì
     * PayOS không có JWT của user. Bảo mật bằng HMAC signature trong body
     * (xem PaymentService.handleWebhook). Phải idempotent vì PayOS có thể retry.
     */
    @PostMapping("/webhook")
    @Operation(summary = "Webhook nhận trạng thái thanh toán từ PayOS (PUBLIC, verify bằng HMAC)")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> body) {
        try {
            paymentService.handleWebhook(body);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (SecurityException se) {
            // Trả 401 cho signature sai để PayOS log lại; KHÔNG để 2xx tránh PayOS coi như đã xử lý.
            log.warn("Webhook PayOS signature sai: {}", se.getMessage());
            return ResponseEntity.status(401).body(Map.of("success", false, "message", se.getMessage()));
        } catch (Exception e) {
            log.error("Webhook PayOS xử lý lỗi: {}", e.getMessage(), e);
            // Trả 200 cho lỗi nghiệp vụ (đã match signature) để PayOS không retry vô tận —
            // mọi vấn đề đã log lại để xử lý offline.
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
