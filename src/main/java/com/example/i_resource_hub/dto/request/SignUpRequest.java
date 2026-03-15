package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SignUpRequest {
    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    private String email;

    private String phone;

    @NotBlank(message = "Loại tài khoản không được để trống")
    private String accountType; // Truyền lên "STUDENT" hoặc "MANAGER"

    // nếu chọn loại tài khoản là STUDENT thì bắt buộc phải có studentCode còn nếu chọn loại tài khoản là MANAGER thì bắt buộc phải có unitId
    private String studentCode;
    private String unitId;

}
