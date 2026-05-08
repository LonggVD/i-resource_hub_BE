package com.example.i_resource_hub.util;

import com.example.i_resource_hub.security.CustomUserDetails;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JWTUtils {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private int jwtExpirationMs;

    // --- HÀM 1: IN THẺ (Tạo Token khi đăng nhập thành công) ---
    public String generateJwtToken(Authentication authentication) {

        // Lấy thông tin người dùng vừa được UserDetailsServiceImpl tìm thấy
        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();

        // Nhồi thêm thông tin quan trọng vào Token (unitId, dataScope)
        Map<String, Object> claims = new HashMap<>();
        claims.put("unitId", userPrincipal.getUnitId());
        claims.put("dataScope", userPrincipal.getDataScope());

        return Jwts.builder()
                .setClaims(claims) // Đưa thông tin phụ vào
                .setSubject((userPrincipal.getUsername())) // Đưa Username làm nhân vật chính
                .setIssuedAt(new Date()) // Thời điểm phát hành thẻ
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs)) // Thời điểm hết hạn
                .signWith(key(), SignatureAlgorithm.HS256) // Đóng dấu mộc bằng chữ ký bí mật
                .compact(); // Ép lại thành 1 chuỗi String dài loằng ngoằng
    }

    // Hàm phụ: Mã hóa cái jwtSecret ở trên thành định dạng Key mà thư viện JWT hiểu
    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(jwtSecret.getBytes())
        ));
    }

    // --- HÀM 2: ĐỌC THẺ (Lấy Username từ Token mà Frontend gửi lên) ---
    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    // --- HÀM 3: KIỂM TRA THẺ (Xem có bị làm giả hay hết hạn không) ---
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken);
            return true; // Thẻ thật, còn hạn
        } catch (JwtException e) {
            System.err.println("Thẻ VIP bị lỗi hoặc đã hết hạn: " + e.getMessage());
        }
        return false;
    }
}