package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.response.PaymentLinkResponse;
import com.example.i_resource_hub.entity.Payment;
import com.example.i_resource_hub.entity.Penalty;
import com.example.i_resource_hub.entity.User;
import com.example.i_resource_hub.repository.PaymentRepository;
import com.example.i_resource_hub.repository.PenaltyRepository;
import com.example.i_resource_hub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    public static final String PENALTY_STATUS_ACTIVE = "ACTIVE";
    public static final String PENALTY_STATUS_PAID = "PAID";
    public static final String PENALTY_STATUS_REVOKED = "REVOKED";

    public static final String PAYMENT_STATUS_PENDING = "PENDING";
    public static final String PAYMENT_STATUS_PAID = "PAID";

    private final PaymentRepository paymentRepository;
    private final PenaltyRepository penaltyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // Timeout chặt: PayOS API chậm = treo cả request từ FE.
    // Connect 5s, read 10s → quá thời gian này coi như fail nhanh, FE hiển thị lỗi.
    private final RestTemplate restTemplate = new org.springframework.boot.web.client.RestTemplateBuilder()
            .setConnectTimeout(java.time.Duration.ofSeconds(5))
            .setReadTimeout(java.time.Duration.ofSeconds(10))
            .build();

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
        // Sắp xếp key theo alphabet, bỏ field rỗng/null (đúng spec PayOS)
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

        // Chỉ phạt đang ACTIVE mới được tạo link. Tránh trường hợp:
        //  - REVOKED: admin đã ân xá → SV trả tiền oan.
        //  - PAID: đã thanh toán rồi → tạo link mới = phí trùng.
        if (!PENALTY_STATUS_ACTIVE.equals(penalty.getStatus())) {
            throw new RuntimeException("Án phạt này không thể thanh toán (trạng thái: "
                    + penalty.getStatus() + ")");
        }
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
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                String checkoutUrl = (String) data.get("checkoutUrl");
                String qrCode = (String) data.get("qrCode");

                // Lưu thông tin thanh toán vào DB
                Payment payment = Payment.builder()
                        .orderCode(orderCode)
                        .amount(penalty.getFineAmount())
                        .status(PAYMENT_STATUS_PENDING)
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

    /**
     * Endpoint FE poll sau khi user redirect về returnUrl.
     * Hỏi PayOS trạng thái rồi áp dụng business logic.
     */
    @Transactional
    public void verifyPayment(long orderCode) throws Exception {
        // Fast path: webhook PayOS thường về trước user (vài giây) → DB đã PAID rồi.
        // Trả về ngay, không cần gọi PayOS API → tiết kiệm 1-3s round trip + tránh hang khi PayOS lag.
        Payment existing = paymentRepository.findByOrderCode(orderCode).orElse(null);
        if (existing != null && PAYMENT_STATUS_PAID.equals(existing.getStatus())) {
            log.debug("verifyPayment fast path: orderCode={} đã PAID qua webhook, bỏ qua call PayOS", orderCode);
            return;
        }

        String url = "https://api-merchant.payos.vn/v2/payment-requests/" + orderCode;

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", clientId);
        headers.set("x-api-key", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("HTTP error: " + response.getStatusCode());
        }
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !"00".equals(responseBody.get("code"))) {
            throw new RuntimeException("PayOS error: " + responseBody);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
        String status = (String) data.get("status");

        if (!"PAID".equals(status)) {
            throw new RuntimeException("Giao dịch chưa được thanh toán. Trạng thái: " + status);
        }

        Number amount = (Number) data.get("amount");
        applyPaidResult(orderCode, amount != null ? amount.doubleValue() : null);
    }

    /**
     * Endpoint cho PayOS gọi webhook khi giao dịch hoàn tất.
     * Sẽ được gọi 1 hoặc nhiều lần (PayOS retry) → phải idempotent.
     *
     * @param body raw payload từ PayOS, có dạng {code, desc, data:{orderCode,amount,...}, signature}
     * @throws SecurityException nếu signature không khớp.
     */
    @Transactional
    public void handleWebhook(Map<String, Object> body) throws Exception {
        if (body == null) {
            throw new IllegalArgumentException("Payload trống");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        String receivedSignature = (String) body.get("signature");
        if (data == null || receivedSignature == null) {
            throw new IllegalArgumentException("Payload thiếu data/signature");
        }

        // PayOS ký HMAC-SHA256 trên các field của `data` (sort key, k=v, join &), key = checksumKey.
        String expected = createSignature(data, checksumKey);
        if (!expected.equalsIgnoreCase(receivedSignature)) {
            log.warn("Webhook signature mismatch. orderCode={}", data.get("orderCode"));
            throw new SecurityException("Webhook signature không hợp lệ");
        }

        Number orderCode = (Number) data.get("orderCode");
        Number amount = (Number) data.get("amount");
        if (orderCode == null) {
            throw new IllegalArgumentException("Webhook thiếu orderCode");
        }

        // PayOS payload có thể bao gồm desc='success' để báo thành công.
        // Để chắc, ta CHỈ apply nếu code=='00'.
        String codeField = (String) body.get("code");
        if (codeField != null && !"00".equals(codeField)) {
            log.info("Webhook code={}, bỏ qua orderCode={}", codeField, orderCode);
            return;
        }

        applyPaidResult(orderCode.longValue(), amount != null ? amount.doubleValue() : null);
    }

    /**
     * Idempotent: nếu đã PAID rồi thì no-op.
     * Validate amount khớp DB (chống forged webhook với amount=1đ).
     * Cập nhật Payment + Penalty + gửi notification.
     */
    private void applyPaidResult(long orderCode, Double receivedAmount) {
        Payment payment = paymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch orderCode=" + orderCode));

        // Idempotent: đã xử lý rồi → return ngay, không update lại / không gửi notif lần 2.
        if (PAYMENT_STATUS_PAID.equals(payment.getStatus())) {
            log.debug("Payment orderCode={} đã PAID trước đó — bỏ qua", orderCode);
            return;
        }

        // Defense-in-depth: chống forged webhook bằng amount bé tí.
        if (receivedAmount != null && payment.getAmount() != null
                && Math.abs(receivedAmount - payment.getAmount()) > 0.01) {
            log.warn("Webhook amount mismatch orderCode={}: received={}, expected={}",
                    orderCode, receivedAmount, payment.getAmount());
            throw new RuntimeException("Số tiền không khớp với giao dịch trong DB");
        }

        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(PAYMENT_STATUS_PAID);
        paymentRepository.save(payment);

        Penalty penalty = payment.getPenalty();
        if (penalty != null && PENALTY_STATUS_ACTIVE.equals(penalty.getStatus())) {
            penalty.setStatus(PENALTY_STATUS_PAID);
            penalty.setPaidAt(now);
            penaltyRepository.save(penalty);

            // Hoàn lại điểm tín nhiệm đã bị trừ khi tạo penalty này.
            // restoreCreditScore là atomic UPDATE: cap 100đ + tự động unlock account nếu score > 0.
            // Snapshot userId trước khi @Modifying(clearAutomatically=true) detach proxy.
            int restorePoints = penalty.getPenaltyPoint();
            String userId = penalty.getUser() != null ? penalty.getUser().getId() : null;
            User refreshed = null;
            boolean wasLocked = penalty.getUser() != null && "LOCKED".equals(penalty.getUser().getStatus());
            boolean wasUnlocked = false;
            if (userId != null && restorePoints > 0) {
                userRepository.restoreCreditScore(userId, restorePoints);
                // Reload sau khi clearAutomatically=true detach context.
                refreshed = userRepository.findById(userId).orElse(null);
                wasUnlocked = wasLocked && refreshed != null && "ACTIVE".equals(refreshed.getStatus());
            } else {
                refreshed = penalty.getUser();
            }

            // Gửi notification cho SV — kèm điểm hiện tại + thông báo unlock (nếu có).
            try {
                String title = "Đã thanh toán tiền phạt";
                StringBuilder body = new StringBuilder();
                body.append("Án phạt ").append(penalty.getPenaltyType())
                        .append(" đã được đóng đủ ")
                        .append(String.format("%,.0f", payment.getAmount())).append("đ. ");
                if (restorePoints > 0) {
                    body.append("Hoàn lại ").append(restorePoints).append(" điểm tín nhiệm");
                    if (refreshed != null) {
                        body.append(" (điểm hiện tại: ").append(refreshed.getCreditScore()).append(")");
                    }
                    body.append(".");
                }
                if (wasUnlocked) {
                    body.append(" Tài khoản đã được mở khoá lại.");
                }
                notificationService.createAndPush(
                        refreshed != null ? refreshed : penalty.getUser(),
                        "PENALTY_PAID", penalty.getId(), title, body.toString());
            } catch (Exception e) {
                log.warn("Không gửi được notification PENALTY_PAID cho penalty {}: {}",
                        penalty.getId(), e.getMessage());
            }
        }
        log.info("Payment orderCode={} → PAID; penalty {} → PAID at {}",
                orderCode, penalty != null ? penalty.getId() : "(none)", now);
    }
}
