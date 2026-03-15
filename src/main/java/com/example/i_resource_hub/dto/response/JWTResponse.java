package com.example.i_resource_hub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JWTResponse {
    private String token;

    @Builder.Default
    private String type = "Bearer";

    private String id;
    private String username;
    private String email;
    private List<String> roles;

    private String unitId;
    private String dataScope;
}
