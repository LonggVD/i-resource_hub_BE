package com.example.i_resource_hub.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendResetPasswordEmail(String to, String resetCode) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("Mã xác nhận khôi phục mật khẩu - iResourceHub");

        String content = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>"
                + "<h2 style='color: #2c3e50; text-align: center;'>Khôi phục mật khẩu</h2>"
                + "<p>Chào bạn,</p>"
                + "<p>Bạn đã yêu cầu khôi phục mật khẩu cho tài khoản iResourceHub. Dưới đây là mã xác nhận của bạn:</p>"
                + "<div style='background-color: #f8f9fa; padding: 15px; border-radius: 5px; text-align: center; margin: 20px 0;'>"
                + "<span style='font-size: 24px; font-weight: bold; color: #3498db; letter-spacing: 5px;'>" + resetCode + "</span>"
                + "</div>"
                + "<p style='color: #e74c3c;'><i>Lưu ý: Mã này sẽ hết hạn sau 5 phút.</i></p>"
                + "<hr style='border: 0; border-top: 1px solid #eee;'>"
                + "<p style='font-size: 12px; color: #7f8c8d; text-align: center;'>Đây là tin nhắn tự động, vui lòng không trả lời email này.</p>"
                + "</div>";

        helper.setText(content, true);
        mailSender.send(message);
    }

    public void sendApprovalEmail(String to, String fullName) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("Tài khoản của bạn đã được phê duyệt - iResourceHub");

        String content = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>"
                + "<h2 style='color: #27ae60; text-align: center;'>Chúc mừng!</h2>"
                + "<p>Chào <b>" + fullName + "</b>,</p>"
                + "<p>Tài khoản của bạn trên hệ thống <b>iResourceHub</b> đã được quản trị viên phê duyệt.</p>"
                + "<p>Bây giờ bạn có thể đăng nhập và sử dụng đầy đủ các tính năng của hệ thống.</p>"
                + "<div style='text-align: center; margin: 30px 0;'>"
                + "<a href='http://localhost:4200/login' style='background-color: #3498db; color: white; padding: 12px 25px; text-decoration: none; border-radius: 5px; font-weight: bold;'>Đăng nhập ngay</a>"
                + "</div>"
                + "<hr style='border: 0; border-top: 1px solid #eee;'>"
                + "<p style='font-size: 12px; color: #7f8c8d; text-align: center;'>Đây là tin nhắn tự động, vui lòng không trả lời email này.</p>"
                + "</div>";

        helper.setText(content, true);
        mailSender.send(message);
    }

    public void sendRejectionEmail(String to, String fullName) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("Thông báo kết quả đăng ký tài khoản - iResourceHub");

        String content = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>"
                + "<h2 style='color: #e74c3c; text-align: center;'>Thông báo</h2>"
                + "<p>Chào <b>" + fullName + "</b>,</p>"
                + "<p>Rất tiếc, yêu cầu đăng ký tài khoản của bạn trên hệ thống <b>iResourceHub</b> đã không được phê duyệt.</p>"
                + "<p>Nếu có bất kỳ thắc mắc nào, vui lòng liên hệ với bộ phận quản trị để được hỗ trợ.</p>"
                + "<hr style='border: 0; border-top: 1px solid #eee;'>"
                + "<p style='font-size: 12px; color: #7f8c8d; text-align: center;'>Đây là tin nhắn tự động, vui lòng không trả lời email này.</p>"
                + "</div>";

        helper.setText(content, true);
        mailSender.send(message);
    }
}
