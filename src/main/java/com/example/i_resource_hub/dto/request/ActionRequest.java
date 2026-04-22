package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActionRequest {
    @NotBlank(message = "Hành động không được để trống")
    private String action; // APPROVE, REJECT, CANCEL

    private String reason;
}
