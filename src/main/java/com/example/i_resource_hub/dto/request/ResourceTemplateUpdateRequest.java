package com.example.i_resource_hub.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResourceTemplateUpdateRequest {
    @NotBlank(message = "Template name must not be blank")
    @Size(max = 100, message = "Template name must be at most 100 characters")
    private String name;
    private String description;
    private Boolean isAutoApprove;
    @Size(max = 255, message = "Image URL must be at most 255 characters")
    private String imageUrl;
    private String categoryId;
    private String unitId;
}