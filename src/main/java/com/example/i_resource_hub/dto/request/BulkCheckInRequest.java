package com.example.i_resource_hub.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkCheckInRequest {
    private List<ItemHandoverRequest> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemHandoverRequest {
        private String bookingId;
        private String serialNumber; // Có thể null nếu dùng item mặc định
    }
}
