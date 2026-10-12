package com.example.domesticflightbookingwebbackend.config;

import com.example.domesticflightbookingwebbackend.entity.Role;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.repository.RoleRepository;
import com.example.domesticflightbookingwebbackend.repository.UserRepository;
import com.example.domesticflightbookingwebbackend.repository.WalletRepository;
import com.example.domesticflightbookingwebbackend.entity.Wallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

/**
 * Tạo tài khoản ADMIN đầu tiên khi database chưa có user nào.
 *
 * <p>KHÔNG seed role/permission ở đây — đã seed bằng migration V2 (role cố định
 * theo BR-RBAC-03). Chỉ dùng để có đường đăng nhập đầu tiên ở môi trường mới.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@flightbooking.local}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@12345}")
    private String adminPassword;

    @Bean
    public ApplicationRunner initAdminAccount() {
        return args -> {
            if (userRepository.count() > 0) {
                return;
            }
            Role adminRole = roleRepository.findByName("ADMIN").orElse(null);
            if (adminRole == null) {
                log.warn("Không tìm thấy role ADMIN — bỏ qua tạo tài khoản quản trị đầu tiên");
                return;
            }

            User admin = new User();
            admin.setEmail(adminEmail);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setFullName("Quản trị hệ thống");
            admin.setRole(adminRole);
            admin.setIsActive(true);
            admin.setIsDeleted(false);
            userRepository.save(admin);

            Wallet wallet = new Wallet();
            wallet.setUser(admin);
            wallet.setBalance(BigDecimal.ZERO);
            walletRepository.save(wallet);

            log.info("Đã tạo tài khoản ADMIN đầu tiên: {} (đổi mật khẩu sau khi đăng nhập)", adminEmail);
        };
    }
}
