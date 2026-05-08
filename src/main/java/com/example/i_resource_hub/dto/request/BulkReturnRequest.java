package com.example.i_resource_hub.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkReturnRequest {
    private List<String> bookingIds;
    private List<ItemDamageRequest> damages;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemDamageRequest {
        private String bookingId;
        private String imageUrl;
        private String description;
    }
}
