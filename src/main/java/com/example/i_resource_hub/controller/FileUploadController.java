package com.example.i_resource_hub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/files")
@Tag(name = "File Upload", description = "API upload ảnh minh chứng")
public class FileUploadController {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    // Public URL prefix - phải khớp với cấu hình Static Resource
    private static final String URL_PREFIX = "/api/files/";

    @Operation(summary = "Upload một hoặc nhiều ảnh", description = "Upload ảnh minh chứng, trả về danh sách URL")
    @PostMapping("/upload")
    public ResponseEntity<List<String>> uploadFiles(
            @RequestParam("files") List<MultipartFile> files) throws IOException {

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        List<String> urls = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            // Validate loại file
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new RuntimeException("Chỉ chấp nhận file ảnh (jpg, png, webp...)");
            }

            // Sinh tên file duy nhất để tránh xung đột
            String originalName = file.getOriginalFilename();
            String ext = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf("."))
                    : ".jpg";
            String fileName = UUID.randomUUID() + ext;

            Path targetPath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Đã upload file: {}", fileName);
            urls.add(URL_PREFIX + fileName);
        }

        return ResponseEntity.ok(urls);
    }
}
