package com.example.i_resource_hub.service;

import com.example.i_resource_hub.entity.Booking;
import com.example.i_resource_hub.repository.BookingRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final BookingRepository bookingRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String[] HEADERS = {
            "STT", "Mã đơn", "Ngày mượn", "Khung giờ", "Thiết bị",
            "Sinh viên", "Mã SV", "Đơn vị quản lý", "Trạng thái", "Ngày tạo"
    };

    // ═══════════════════════════════════════════════════════════
    //  EXCEL (.xlsx)
    // ═══════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public byte[] generateBookingExcel(LocalDate from, LocalDate to) throws IOException {
        List<Booking> bookings = bookingRepository.findByBookingDateBetween(from, to);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Bao cao don muon");

            // ── Tiêu đề (row 0) ──
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BÁO CÁO DANH SÁCH ĐƠN MƯỢN");
            titleCell.setCellStyle(buildTitleStyle(wb));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            // ── Khoảng thời gian (row 1) ──
            Row periodRow = sheet.createRow(1);
            Cell periodCell = periodRow.createCell(0);
            periodCell.setCellValue("Từ ngày " + from.format(DATE_FMT) + " đến ngày " + to.format(DATE_FMT));
            periodCell.setCellStyle(buildSubtitleStyle(wb));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, HEADERS.length - 1));

            // ── Header row (row 3) ──
            Row headerRow = sheet.createRow(3);
            CellStyle headerStyle = buildHeaderStyle(wb);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── Data rows ──
            CellStyle bodyStyle = buildBodyStyle(wb);
            int rowNum = 4;
            int stt = 1;
            for (Booking b : bookings) {
                Row row = sheet.createRow(rowNum++);
                writeCell(row, 0, String.valueOf(stt++), bodyStyle);
                writeCell(row, 1, b.getId(), bodyStyle);
                writeCell(row, 2, b.getBookingDate() != null ? b.getBookingDate().format(DATE_FMT) : "", bodyStyle);
                writeCell(row, 3, formatSlot(b), bodyStyle);
                writeCell(row, 4, getDeviceName(b), bodyStyle);
                writeCell(row, 5, b.getUser() != null ? b.getUser().getFullName() : "", bodyStyle);
                writeCell(row, 6, b.getUser() != null ? b.getUser().getUsername() : "", bodyStyle);
                writeCell(row, 7, b.getManagedByUnit() != null ? b.getManagedByUnit().getUnitName() : "", bodyStyle);
                writeCell(row, 8, b.getStatus(), bodyStyle);
                writeCell(row, 9, b.getCreatedAt() != null ? b.getCreatedAt().toString().substring(0, 16) : "", bodyStyle);
            }

            // ── Footer ──
            if (bookings.isEmpty()) {
                Row emptyRow = sheet.createRow(rowNum);
                Cell emptyCell = emptyRow.createCell(0);
                emptyCell.setCellValue("(Không có dữ liệu trong khoảng thời gian này)");
                emptyCell.setCellStyle(buildEmptyStyle(wb));
                sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, HEADERS.length - 1));
            } else {
                Row totalRow = sheet.createRow(rowNum + 1);
                Cell totalCell = totalRow.createCell(0);
                totalCell.setCellValue("Tổng số đơn: " + bookings.size());
                totalCell.setCellStyle(buildTotalStyle(wb));
                sheet.addMergedRegion(new CellRangeAddress(rowNum + 1, rowNum + 1, 0, HEADERS.length - 1));
            }

            // Auto-size cột
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PDF
    // ═══════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public byte[] generateBookingPdf(LocalDate from, LocalDate to) {
        List<Booking> bookings = bookingRepository.findByBookingDateBetween(from, to);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
            PdfWriter.getInstance(doc, out);
            doc.open();

            // ── Tiêu đề ──
            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(15, 23, 42));
            Paragraph title = new Paragraph("BÁO CÁO DANH SÁCH ĐƠN MƯỢN", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            // ── Khoảng thời gian ──
            Font subFont = new Font(Font.HELVETICA, 11, Font.ITALIC, new Color(71, 85, 105));
            Paragraph subtitle = new Paragraph(
                    "Từ ngày " + from.format(DATE_FMT) + " đến ngày " + to.format(DATE_FMT), subFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(15);
            doc.add(subtitle);

            // ── Bảng dữ liệu ──
            PdfPTable table = new PdfPTable(HEADERS.length);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.5f, 1.6f, 1f, 1.4f, 2.2f, 1.8f, 1f, 1.6f, 1f, 1.2f});

            // Header
            Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
            Color headerBg = new Color(99, 102, 241);
            for (String h : HEADERS) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(headerBg);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(6);
                table.addCell(cell);
            }

            // Body
            Font bodyFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(30, 41, 59));
            int stt = 1;
            for (Booking b : bookings) {
                addPdfCell(table, String.valueOf(stt++), bodyFont, Element.ALIGN_CENTER);
                addPdfCell(table, b.getId() != null ? b.getId().substring(0, 8) + "…" : "", bodyFont, Element.ALIGN_LEFT);
                addPdfCell(table, b.getBookingDate() != null ? b.getBookingDate().format(DATE_FMT) : "", bodyFont, Element.ALIGN_CENTER);
                addPdfCell(table, formatSlot(b), bodyFont, Element.ALIGN_LEFT);
                addPdfCell(table, getDeviceName(b), bodyFont, Element.ALIGN_LEFT);
                addPdfCell(table, b.getUser() != null ? b.getUser().getFullName() : "", bodyFont, Element.ALIGN_LEFT);
                addPdfCell(table, b.getUser() != null ? b.getUser().getUsername() : "", bodyFont, Element.ALIGN_LEFT);
                addPdfCell(table, b.getManagedByUnit() != null ? b.getManagedByUnit().getUnitName() : "", bodyFont, Element.ALIGN_LEFT);
                addPdfCell(table, b.getStatus(), bodyFont, Element.ALIGN_CENTER);
                addPdfCell(table, b.getCreatedAt() != null ? b.getCreatedAt().toString().substring(0, 10) : "", bodyFont, Element.ALIGN_CENTER);
            }

            if (bookings.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("(Không có dữ liệu trong khoảng thời gian này)",
                        new Font(Font.HELVETICA, 10, Font.ITALIC, new Color(148, 163, 184))));
                empty.setColspan(HEADERS.length);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                empty.setPadding(20);
                table.addCell(empty);
            }

            doc.add(table);

            // ── Footer tổng số ──
            doc.add(new Paragraph(" "));
            Font totalFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(15, 23, 42));
            Paragraph total = new Paragraph("Tổng số đơn: " + bookings.size(), totalFont);
            total.setAlignment(Element.ALIGN_RIGHT);
            doc.add(total);

            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            log.error("Lỗi xuất PDF", ex);
            throw new RuntimeException("Không thể xuất PDF: " + ex.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════
    private String formatSlot(Booking b) {
        if (b.getSlot() == null) return "";
        return b.getSlot().getSlotName() + " (" + b.getSlot().getStartTime() + "-" + b.getSlot().getEndTime() + ")";
    }

    private String getDeviceName(Booking b) {
        if (b.getResourceItem() == null) return "";
        if (b.getResourceItem().getTemplate() != null) {
            return b.getResourceItem().getTemplate().getName();
        }
        return b.getResourceItem().getSerialNumber();
    }

    private void writeCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void addPdfCell(PdfPTable table, String value, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(value != null ? value : "", font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        table.addCell(cell);
    }

    // ═══════════════════════════════════════════════════════════
    //  EXCEL STYLES
    // ═══════════════════════════════════════════════════════════
    private CellStyle buildTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle buildSubtitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setItalic(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style);
        return style;
    }

    private CellStyle buildBodyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style);
        return style;
    }

    private CellStyle buildEmptyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle buildTotalStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
