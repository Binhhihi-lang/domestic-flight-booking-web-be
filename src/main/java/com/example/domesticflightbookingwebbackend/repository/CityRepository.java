package com.example.domesticflightbookingwebbackend.repository;

import com.example.domesticflightbookingwebbackend.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long>, JpaSpecificationExecutor<City> {

    Optional<City> findByCityCodeAndIsDeletedFalse(String cityCode);

    boolean existsByCityCodeAndIsDeletedFalse(String cityCode);
}
