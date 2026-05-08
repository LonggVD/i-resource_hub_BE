package com.example.i_resource_hub.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class ResourceItemBulkCreateRequest {
    private String templateId;
    private List<String> serialNumbers;
}
