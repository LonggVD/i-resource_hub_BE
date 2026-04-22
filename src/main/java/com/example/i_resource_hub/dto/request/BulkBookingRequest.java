package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class BulkBookingRequest {
    
    @NotEmpty(message = "Danh sách mượn không được để trống")
    private List<BookingItemRequest> items;

    private String purpose;

    @Data
    public static class BookingItemRequest {
        @NotNull(message = "Mẫu tài nguyên không được để trống")
        private String resourceTemplateId;
        
        private Integer quantity = 1;

        @NotNull(message = "Ngày mượn không được để trống")
        private LocalDate bookingDate;

        @NotNull(message = "Khung giờ không được để trống")
        private String slotId;
    }
}
