package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.common.ListQuery;
import com.example.domesticflightbookingwebbackend.entity.Aircraft;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.City;
import com.example.domesticflightbookingwebbackend.entity.NewsCategory;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.SeatType;
import com.example.domesticflightbookingwebbackend.mapper.AircraftMapper;
import com.example.domesticflightbookingwebbackend.mapper.AirportMapper;
import com.example.domesticflightbookingwebbackend.mapper.CityMapper;
import com.example.domesticflightbookingwebbackend.mapper.SeatTypeMapper;
import com.example.domesticflightbookingwebbackend.repository.AircraftRepository;
import com.example.domesticflightbookingwebbackend.repository.AircraftSeatRepository;
import com.example.domesticflightbookingwebbackend.repository.AirportRepository;
import com.example.domesticflightbookingwebbackend.repository.CityRepository;
import com.example.domesticflightbookingwebbackend.repository.NewsCategoryMappingRepository;
import com.example.domesticflightbookingwebbackend.repository.NewsCategoryRepository;
import com.example.domesticflightbookingwebbackend.repository.SeatTypeRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kiểm tra 5 bộ lọc danh sách Admin (status, manufacturer, seat_class) được áp dụng vào Specification. */
@SuppressWarnings({"unchecked", "rawtypes"})
class AdminMasterDataFilterTest {

    @Test
    void cityService_locTheoStatus() {
        CityRepository repo = mock(CityRepository.class);
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        CityService service = new CityService(repo, new CityMapper());

        service.list(new ListQuery(), Map.of("status", "INACTIVE"));

        ArgumentCaptor<Specification<City>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), any(Pageable.class));

        Root<City> root = mock(Root.class);
        CriteriaQuery<?> cq = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path isActivePath = mock(Path.class);
        when(root.get("isDeleted")).thenReturn(mock(Path.class));
        when(root.get("isActive")).thenReturn(isActivePath);
        when(cb.isFalse(any())).thenReturn(mock(Predicate.class));
        when(cb.equal(any(), any())).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        captor.getValue().toPredicate(root, cq, cb);

        verify(cb).equal(isActivePath, false);
    }

    @Test
    void airportService_locTheoStatus() {
        AirportRepository repo = mock(AirportRepository.class);
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        AirportService service = new AirportService(repo, mock(CityRepository.class), new AirportMapper(new CityMapper()));

        service.list(new ListQuery(), Map.of("status", "ACTIVE"));

        ArgumentCaptor<Specification<Airport>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), any(Pageable.class));

        Root<Airport> root = mock(Root.class);
        CriteriaQuery<?> cq = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path isActivePath = mock(Path.class);
        when(root.get("isDeleted")).thenReturn(mock(Path.class));
        when(root.get("isActive")).thenReturn(isActivePath);
        when(cb.isFalse(any())).thenReturn(mock(Predicate.class));
        when(cb.equal(any(), any())).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        captor.getValue().toPredicate(root, cq, cb);

        verify(cb).equal(isActivePath, true);
    }

    @Test
    void aircraftService_locTheoStatusVaManufacturerLikeIgnoreCase() {
        AircraftRepository repo = mock(AircraftRepository.class);
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        AircraftService service = new AircraftService(repo, new AircraftMapper());

        service.list(new ListQuery(), Map.of("status", "ACTIVE", "manufacturer", "Airbus"));

        ArgumentCaptor<Specification<Aircraft>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), any(Pageable.class));

        Root<Aircraft> root = mock(Root.class);
        CriteriaQuery<?> cq = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path isActivePath = mock(Path.class);
        Path manufacturerPath = mock(Path.class);
        Expression<String> lowerExpr = mock(Expression.class);
        when(root.get("isDeleted")).thenReturn(mock(Path.class));
        when(root.get("isActive")).thenReturn(isActivePath);
        when(root.get("manufacturer")).thenReturn(manufacturerPath);
        when(cb.isFalse(any())).thenReturn(mock(Predicate.class));
        when(cb.equal(any(), any())).thenReturn(mock(Predicate.class));
        when(cb.lower(manufacturerPath)).thenReturn(lowerExpr);
        when(cb.like(eq(lowerExpr), eq("%airbus%"))).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        captor.getValue().toPredicate(root, cq, cb);

        verify(cb).equal(isActivePath, true);
        verify(cb).like(lowerExpr, "%airbus%");
    }

    @Test
    void seatTypeService_locTheoSeatClassVaStatus() {
        SeatTypeRepository repo = mock(SeatTypeRepository.class);
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        SeatTypeService service = new SeatTypeService(repo, mock(AircraftSeatRepository.class), new SeatTypeMapper());

        service.list(new ListQuery(), Map.of("seat_class", "business", "status", "ACTIVE"));

        ArgumentCaptor<Specification<SeatType>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), any(Pageable.class));

        Root<SeatType> root = mock(Root.class);
        CriteriaQuery<?> cq = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path seatClassPath = mock(Path.class);
        Path isActivePath = mock(Path.class);
        when(root.get("isDeleted")).thenReturn(mock(Path.class));
        when(root.get("seatClass")).thenReturn(seatClassPath);
        when(root.get("isActive")).thenReturn(isActivePath);
        when(cb.isFalse(any())).thenReturn(mock(Predicate.class));
        when(cb.equal(any(), any())).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        captor.getValue().toPredicate(root, cq, cb);

        verify(cb).equal(seatClassPath, SeatClass.BUSINESS);
        verify(cb).equal(isActivePath, true);
    }

    @Test
    void newsCategoryService_locTheoStatus() {
        NewsCategoryRepository repo = mock(NewsCategoryRepository.class);
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        NewsCategoryService service = new NewsCategoryService(repo, mock(NewsCategoryMappingRepository.class));

        service.list(new ListQuery(), Map.of("status", "INACTIVE"));

        ArgumentCaptor<Specification<NewsCategory>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), any(Pageable.class));

        Root<NewsCategory> root = mock(Root.class);
        CriteriaQuery<?> cq = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path isActivePath = mock(Path.class);
        when(root.get("isDeleted")).thenReturn(mock(Path.class));
        when(root.get("isActive")).thenReturn(isActivePath);
        when(cb.isFalse(any())).thenReturn(mock(Predicate.class));
        when(cb.equal(any(), any())).thenReturn(mock(Predicate.class));
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        captor.getValue().toPredicate(root, cq, cb);

        verify(cb).equal(isActivePath, false);
    }
}
