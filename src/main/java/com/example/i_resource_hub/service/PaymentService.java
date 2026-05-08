package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.response.PaymentLinkResponse;
import com.example.i_resource_hub.entity.Payment;
import com.example.i_resource_hub.entity.Penalty;
import com.example.i_resource_hub.repository.PaymentRepository;
import com.example.i_resource_hub.repository.PenaltyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PenaltyRepository penaltyRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${client_id}")
    private String clientId;

    @Value("${api_key}")
    private String apiKey;

    @Value("${checksum_key}")
    private String checksumKey;

    @Value("${return_url}")
    private String returnUrl;

    @Value("${cancel_url}")
    private String cancelUrl;

    private String createSignature(Map<String, Object> data, String checksumKey) throws Exception {
        // Sắp xếp key theo alphabet
        String dataStr = data.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(e -> e.getValue() != null && !e.getValue().toString().isEmpty())
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(checksumKey.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(dataStr.getBytes());
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Transactional
    public PaymentLinkResponse createPaymentLink(String penaltyId) throws Exception {
        Penalty penalty = penaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy án phạt"));

        if (penalty.getFineAmount() == null || penalty.getFineAmount() <= 0) {
            throw new RuntimeException("Án phạt này không có tiền phạt");
        }

        long orderCode = System.currentTimeMillis() / 1000;
        String url = "https://api-merchant.payos.vn/v2/payment-requests";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", penalty.getFineAmount().intValue());
        body.put("description", "Phat #" + penaltyId.substring(0, 8));
        body.put("returnUrl", returnUrl + "?orderCode=" + orderCode + "&status=PAID");
        body.put("cancelUrl", cancelUrl + "?orderCode=" + orderCode + "&status=CANCELLED");

        // Tạo signature thủ công
        String signature = createSignature(body, checksumKey);
        body.put("signature", signature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", clientId);
        headers.set("x-api-key", apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && "00".equals(responseBody.get("code"))) {
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                String checkoutUrl = (String) data.get("checkoutUrl");
                String qrCode = (String) data.get("qrCode");

                // Lưu thông tin thanh toán vào DB
                Payment payment = Payment.builder()
                        .orderCode(orderCode)
                        .amount(penalty.getFineAmount())
                        .status("PENDING")
                        .checkoutUrl(checkoutUrl)
                        .penalty(penalty)
                        .user(penalty.getUser())
                        .build();
                paymentRepository.save(payment);

                return PaymentLinkResponse.builder()
                        .checkoutUrl(checkoutUrl)
                        .qrCode(qrCode)
                        .orderCode(String.valueOf(orderCode))
                        .build();
            } else {
                throw new RuntimeException("PayOS error: " + responseBody);
            }
        } else {
            throw new RuntimeException("HTTP error: " + response.getStatusCode());
        }
    }

    @Transactional
    public void verifyPayment(long orderCode) throws Exception {
        String url = "https://api-merchant.payos.vn/v2/payment-requests/" + orderCode;

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", clientId);
        headers.set("x-api-key", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && "00".equals(responseBody.get("code"))) {
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                String status = (String) data.get("status");

                Payment payment = paymentRepository.findByOrderCode(orderCode)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch"));

                if ("PAID".equals(status)) {
                    payment.setStatus("PAID");
                    Penalty penalty = payment.getPenalty();
                    if (penalty != null) {
                        penalty.setStatus("COMPLETED");
                        penaltyRepository.save(penalty);
                    }
                    paymentRepository.save(payment);
                } else {
                    throw new RuntimeException("Giao dịch chưa được thanh toán. Trạng thái: " + status);
                }
            } else {
                throw new RuntimeException("PayOS error: " + responseBody);
            }
        } else {
            throw new RuntimeException("HTTP error: " + response.getStatusCode());
        }
    }
}
