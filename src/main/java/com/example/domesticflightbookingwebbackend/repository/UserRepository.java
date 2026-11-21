package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailAndIsDeletedFalse(String email);

    boolean existsByEmailAndIsDeletedFalse(String email);

    /** Lấy danh sách người dùng đang hoạt động theo tập tên role (dùng để gửi thông báo vận hành cho STAFF/ADMIN). */
    List<User> findByRoleNameInAndIsActiveTrueAndIsDeletedFalse(Collection<String> roleNames);
}
