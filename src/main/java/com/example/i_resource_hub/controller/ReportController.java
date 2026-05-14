package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "API xuất báo cáo Excel / PDF")
@PreAuthorize("hasAnyAuthority('RESOURCE_MANAGE', 'ADMIN')")
public class ReportController {

    private final ReportService reportService;

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @GetMapping("/bookings/excel")
    @Operation(summary = "Xuất danh sách đơn mượn ra file Excel (.xlsx)")
    public ResponseEntity<byte[]> exportBookingExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws IOException {
        byte[] data = reportService.generateBookingExcel(from, to);
        String filename = String.format("bookings-%s-%s.xlsx", from.format(FILE_DATE), to.format(FILE_DATE));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/bookings/pdf")
    @Operation(summary = "Xuất danh sách đơn mượn ra file PDF")
    public ResponseEntity<byte[]> exportBookingPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] data = reportService.generateBookingPdf(from, to);
        String filename = String.format("bookings-%s-%s.pdf", from.format(FILE_DATE), to.format(FILE_DATE));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }
}
