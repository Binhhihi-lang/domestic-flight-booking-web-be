package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.response.common.ImageUploadResponse;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dịch vụ tải ảnh lên cho khu vực quản trị (logo hãng bay, ảnh thành phố, ảnh tin tức, khuyến mãi).
 *
 * <p>Ưu tiên tải lên Cloudinary nếu đã cấu hình đầy đủ thông tin thật; khi chạy cục bộ chưa có
 * Cloudinary sẽ lưu an toàn vào thư mục {@code uploads/} với tên UUID và trả URL phục vụ tại
 * {@code /uploads/<filename>}.
 */
@Slf4j
@Service
public class ImageUploadService {

    public static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final String ADMIN_CLOUDINARY_FOLDER = "flight-booking/admin";

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif",
            "image/svg+xml", ".svg"
    );

    private final CloudinaryService cloudinaryService;
    private final String uploadDir;
    private final String publicBaseUrl;

    public ImageUploadService(
            CloudinaryService cloudinaryService,
            @Value("${app.upload.dir:uploads}") String uploadDir,
            @Value("${app.upload.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.cloudinaryService = cloudinaryService;
        this.uploadDir = uploadDir;
        this.publicBaseUrl = publicBaseUrl;
    }

    public ImageUploadResponse uploadAdminImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "File ảnh không được để trống");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Kích thước ảnh không được vượt quá 5MB");
        }

        String normalizedContentType = normalizeContentType(file.getContentType());
        String extension = ALLOWED_CONTENT_TYPES.get(normalizedContentType);
        if (extension == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Chỉ chấp nhận file ảnh định dạng JPEG, PNG, WEBP, GIF hoặc SVG");
        }

        if (cloudinaryService.isConfigured()) {
            String url = cloudinaryService.uploadImage(file, ADMIN_CLOUDINARY_FOLDER);
            String storedName = extractFileName(url, UUID.randomUUID() + extension);
            log.info("Đã tải ảnh quản trị lên Cloudinary: {}", storedName);
            return new ImageUploadResponse(url, storedName, normalizedContentType, file.getSize());
        }

        return storeLocally(file, normalizedContentType, extension);
    }

    private ImageUploadResponse storeLocally(MultipartFile file, String contentType, String extension) {
        String fileName = UUID.randomUUID() + extension;
        try {
            Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadRoot);
            Path target = uploadRoot.resolve(fileName).normalize();
            if (!target.startsWith(uploadRoot)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Đường dẫn lưu tệp không hợp lệ");
            }
            Files.write(target, file.getBytes());

            String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            String url = base.isEmpty() ? "/uploads/" + fileName : base + "/uploads/" + fileName;
            log.info("Đã lưu ảnh quản trị cục bộ: {} -> {}", target, url);
            return new ImageUploadResponse(url, fileName, contentType, file.getSize());
        } catch (IOException e) {
            log.error("Lưu ảnh quản trị cục bộ thất bại", e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Lưu file ảnh thất bại");
        }
    }

    private String normalizeContentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String base = raw.split(";")[0];
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private String extractFileName(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }
        int slash = url.lastIndexOf('/');
        return slash >= 0 && slash + 1 < url.length() ? url.substring(slash + 1) : fallback;
    }
}
