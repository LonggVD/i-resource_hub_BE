package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditScoreRequest {
    @NotNull(message = "Amount is required")
    private Integer amount; // Can be positive or negative
    
    private String reason;
}
