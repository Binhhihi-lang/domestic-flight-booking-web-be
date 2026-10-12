package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {

    Optional<City> findByCityCodeAndIsDeletedFalse(String cityCode);

    boolean existsByCityCodeAndIsDeletedFalse(String cityCode);
}
