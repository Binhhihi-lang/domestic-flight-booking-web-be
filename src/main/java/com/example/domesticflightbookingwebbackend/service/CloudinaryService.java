package com.example.domesticflightbookingwebbackend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** Upload ảnh lên Cloudinary. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    /** Thư mục trên Cloudinary chứa avatar người dùng. */
    private static final String AVATAR_FOLDER = "flight-booking/avatars";

    private final Cloudinary cloudinary;

    /**
     * Upload một file ảnh, trả URL công khai.
     * Cấu hình sai hoặc Cloudinary lỗi → 9999 (lỗi hệ thống), không lộ chi tiết.
     */
    public String uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "File ảnh không được để trống");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Chỉ chấp nhận file ảnh");
        }

        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", AVATAR_FOLDER,
                    "resource_type", "image"));
            Object url = result.get("secure_url");
            if (url == null) {
                throw new AppException(ErrorCode.INTERNAL_ERROR, "Upload ảnh thất bại");
            }
            return url.toString();
        } catch (IOException e) {
            log.error("Upload avatar lên Cloudinary thất bại", e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Upload ảnh thất bại");
        }
    }
}
