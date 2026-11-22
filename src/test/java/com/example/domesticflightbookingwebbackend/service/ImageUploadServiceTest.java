package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.response.common.ImageUploadResponse;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageUploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadAdminImage_chuaCauHinhCloudinary_luuCucBoVaTraUrlHopLe() throws Exception {
        CloudinaryService cloudinaryService = mock(CloudinaryService.class);
        when(cloudinaryService.isConfigured()).thenReturn(false);

        ImageUploadService service = new ImageUploadService(
                cloudinaryService, tempDir.toString(), "http://localhost:8080");

        byte[] content = new byte[]{1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", content);

        ImageUploadResponse response = service.uploadAdminImage(file);

        assertThat(response.url()).startsWith("http://localhost:8080/uploads/");
        assertThat(response.url()).endsWith(".png");
        assertThat(response.url().length()).isLessThan(500);
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.size()).isEqualTo(4L);

        Path saved = tempDir.resolve(response.fileName());
        assertThat(Files.exists(saved)).isTrue();
        assertThat(Files.readAllBytes(saved)).isEqualTo(content);
    }

    @Test
    void uploadAdminImage_daCauHinhCloudinary_uyQuyenCloudinary() {
        CloudinaryService cloudinaryService = mock(CloudinaryService.class);
        when(cloudinaryService.isConfigured()).thenReturn(true);
        when(cloudinaryService.uploadImage(any(), eq("flight-booking/admin")))
                .thenReturn("https://res.cloudinary.com/demo/image/upload/v1/banner.webp");

        ImageUploadService service = new ImageUploadService(
                cloudinaryService, tempDir.toString(), "http://localhost:8080");

        MockMultipartFile file = new MockMultipartFile("file", "banner.webp", "image/webp", new byte[]{9, 8, 7});
        ImageUploadResponse response = service.uploadAdminImage(file);

        verify(cloudinaryService).uploadImage(file, "flight-booking/admin");
        assertThat(response.url()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/banner.webp");
        assertThat(response.fileName()).isEqualTo("banner.webp");
    }

    @Test
    void uploadAdminImage_saiDinhDangHoacQua5MB_nemValidationError() {
        CloudinaryService cloudinaryService = mock(CloudinaryService.class);
        ImageUploadService service = new ImageUploadService(
                cloudinaryService, tempDir.toString(), "http://localhost:8080");

        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> service.uploadAdminImage(empty))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("trống");

        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1, 2});
        assertThatThrownBy(() -> service.uploadAdminImage(pdf))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("JPEG");

        byte[] tooBig = new byte[(int) ImageUploadService.MAX_IMAGE_SIZE_BYTES + 1];
        MockMultipartFile oversized = new MockMultipartFile("file", "huge.jpg", "image/jpeg", tooBig);
        assertThatThrownBy(() -> service.uploadAdminImage(oversized))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void uploadAdminImage_chapNhanDu5DinhDangAnh() {
        CloudinaryService cloudinaryService = mock(CloudinaryService.class);
        when(cloudinaryService.isConfigured()).thenReturn(false);
        ImageUploadService service = new ImageUploadService(
                cloudinaryService, tempDir.toString(), "http://localhost:8080");

        for (String mime : new String[]{"image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml"}) {
            MockMultipartFile file = new MockMultipartFile("file", "img", mime, new byte[]{1, 2, 3});
            ImageUploadResponse res = service.uploadAdminImage(file);
            assertThat(res.url()).startsWith("http://localhost:8080/uploads/");
        }
    }
}
