package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EvidenceRequest {
    @NotBlank(message = "ID Đơn mượn không được để trống")
    private String bookingId;

    @NotBlank(message = "Loại minh chứng không được để trống")
    private String evidenceType; // CHECK_IN, CHECK_OUT, DAMAGE

    @NotBlank(message = "Đường dẫn ảnh không được để trống")
    private String imageUrl;

    private String description;
}
