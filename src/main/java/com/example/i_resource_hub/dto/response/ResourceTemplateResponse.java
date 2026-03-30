package com.example.i_resource_hub.dto.response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceTemplateResponse {
    private String id;
    private String name;
    private String description;
    private Boolean isAutoApprove;
    private String imageUrl;
    private CategorySummary category;
    private OrganizationUnitSummary unit;
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySummary {
        private String id;
        private String categoryName;
    }
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizationUnitSummary {
        private String id;
        private String unitName;
    }
}