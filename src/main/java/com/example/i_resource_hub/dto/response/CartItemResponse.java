package com.example.i_resource_hub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class CartItemResponse {
    private String id;
    private String resourceTemplateId;
    private String resourceName;
    private String imageUrl;
    private String unitName;
    private Integer quantity;
    private LocalDate bookingDate;
    private String slotId;
    private String slotName;
}
