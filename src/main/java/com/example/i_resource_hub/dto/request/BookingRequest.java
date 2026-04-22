package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class BookingRequest {
    @NotNull(message = "Mẫu tài nguyên không được để trống")
    private String resourceTemplateId;

    private String resourceItemId;

    @NotNull(message = "Ngày mượn không được để trống")
    private LocalDate bookingDate;

    @NotNull(message = "Khung giờ không được để trống")
    private String slotId;

    private Integer quantity = 1; // Mặc định là mượn 1 cái

    private String purpose;

    private Set<String> participantIds;
}
