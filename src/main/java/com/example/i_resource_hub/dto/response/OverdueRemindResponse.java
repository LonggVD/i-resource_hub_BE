package com.example.i_resource_hub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OverdueRemindResponse {
    private int requested;
    private int sent;
    private List<String> skippedBookingIds;
    private List<String> failedBookingIds;
}
