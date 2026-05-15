package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OverdueRemindRequest {
    @NotEmpty(message = "Danh sách bookingIds không được rỗng")
    private List<String> bookingIds;
}
