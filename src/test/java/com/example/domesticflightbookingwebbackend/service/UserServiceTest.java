package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.entity.Role;
import com.example.domesticflightbookingwebbackend.entity.User;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.mapper.UserMapper;
import com.example.domesticflightbookingwebbackend.repository.RoleRepository;
import com.example.domesticflightbookingwebbackend.repository.UserRepository;
import com.example.domesticflightbookingwebbackend.repository.redis.RefreshTokenRepository;
import com.example.domesticflightbookingwebbackend.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Test ràng buộc ADMIN không xóa ADMIN (chốt 14/10/2026, vá ở cụm 9). */
class UserServiceTest {

    private UserRepository userRepository;
    private UserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new UserService(
                userRepository,
                mock(RoleRepository.class),
                mock(PasswordEncoder.class),
                new UserMapper(),
                mock(UserDetailsServiceImpl.class),
                mock(RefreshTokenRepository.class));
    }

    private User user(String roleName) {
        Role role = new Role();
        role.setName(roleName);

        User u = new User();
        u.setId(9L);
        u.setEmail("target@example.com");
        u.setRole(role);
        u.setIsActive(true);
        u.setIsDeleted(false);
        return u;
    }

    @Test
    void xoaAdmin_nem1006() {
        when(userRepository.findById(9L)).thenReturn(Optional.of(user("ADMIN")));

        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ADMIN");

        verify(userRepository, never()).save(any());
    }

    @Test
    void xoaStaff_thanhCong() {
        User staff = user("STAFF");
        when(userRepository.findById(9L)).thenReturn(Optional.of(staff));

        service.delete(9L);

        assertThat(staff.getIsDeleted()).isTrue();
        assertThat(staff.getIsActive()).isFalse();
        verify(userRepository).save(staff);
    }

    @Test
    void xoaCustomer_thanhCong() {
        User customer = user("CUSTOMER");
        when(userRepository.findById(9L)).thenReturn(Optional.of(customer));

        service.delete(9L);

        verify(userRepository).save(customer);
    }
}