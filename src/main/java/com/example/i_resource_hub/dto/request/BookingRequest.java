package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class BookingRequest {

    @NotBlank(message = "Mẫu tài nguyên không được để trống")
    private String resourceTemplateId;

    private String resourceItemId;

    @NotNull(message = "Ngày mượn không được để trống")
    @FutureOrPresent(message = "Ngày mượn không thể là ngày trong quá khứ")
    private LocalDate bookingDate;

    @NotBlank(message = "Khung giờ không được để trống")
    private String slotId;

    @Min(value = 1, message = "Số lượng tối thiểu là 1")
    @Max(value = 50, message = "Số lượng tối đa là 50")
    private Integer quantity = 1;

    @Size(max = 500, message = "Mục đích tối đa 500 ký tự")
    private String purpose;

    private Set<String> participantIds;
}
