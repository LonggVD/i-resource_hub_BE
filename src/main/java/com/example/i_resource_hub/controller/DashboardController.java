package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.request.OverdueRemindRequest;
import com.example.i_resource_hub.dto.response.DashboardResponse;
import com.example.i_resource_hub.dto.response.OverdueRemindResponse;
import com.example.i_resource_hub.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Các API thống kê cho màn hình Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('RESOURCE_MANAGE', 'ADMIN')")
    @Operation(summary = "Lấy dữ liệu thống kê tổng hợp cho Dashboard")
    public ResponseEntity<DashboardResponse> getDashboardStats() {
        return ResponseEntity.ok(dashboardService.getDashboardStats());
    }

    @PostMapping("/remind-overdue")
    @PreAuthorize("hasAnyAuthority('RESOURCE_MANAGE', 'ADMIN')")
    @Operation(summary = "Gửi email nhắc nhở trả thiết bị cho các booking quá hạn")
    public ResponseEntity<OverdueRemindResponse> remindOverdue(@Valid @RequestBody OverdueRemindRequest request) {
        return ResponseEntity.ok(dashboardService.remindOverdue(request));
    }
}
