package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationUnitUpdateRequest {
    @NotBlank(message = "Ten don vi khong duoc de trong")
    private String unitName;

    @NotBlank(message = "Loai don vi khong duoc de trong")
    private String unitType;
}
