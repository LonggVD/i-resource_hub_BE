package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceItemCreateRequest {

    @NotBlank(message = "Mẫu tài nguyên không được để trống")
    private String templateId;

    @NotBlank(message = "Số serial không được để trống")
    @Size(max = 50, message = "Số serial tối đa 50 ký tự")
    private String serialNumber;

    @PastOrPresent(message = "Ngày mua không thể là ngày trong tương lai")
    private LocalDate purchaseDate;

    private LocalDate warrantyExpiry;

    @Size(max = 20, message = "Trạng thái tối đa 20 ký tự")
    private String conditionStatus;

    @Size(max = 20, message = "Trạng thái tối đa 20 ký tự")
    private String status;

    private String unitId;
}
