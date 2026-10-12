package com.example.domesticflightbookingwebbackend.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageMapperTest {

    @Test
    void toPageData_chuyenPage0BasedThanhPage1Based() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 20), 137);

        PageData<String> data = PageMapper.toPageData(page);

        assertThat(data.items()).containsExactly("a", "b");
        assertThat(data.pagination().page()).isEqualTo(1);   // Spring 0-based → API 1-based
        assertThat(data.pagination().size()).isEqualTo(20);
        assertThat(data.pagination().totalItems()).isEqualTo(137);
        assertThat(data.pagination().totalPages()).isEqualTo(7);
    }

    @Test
    void toPageData_voiMapper_chuyenTungPhanTu() {
        Page<Integer> page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(0, 10), 3);

        PageData<String> data = PageMapper.toPageData(page, i -> "n" + i);

        assertThat(data.items()).containsExactly("n1", "n2", "n3");
        assertThat(data.pagination().totalItems()).isEqualTo(3);
    }

    @Test
    void toPageData_ketQuaRongLaTrangThaiHopLe() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        PageData<String> data = PageMapper.toPageData(page);

        assertThat(data.items()).isEmpty();
        assertThat(data.pagination().totalItems()).isZero();
        assertThat(data.pagination().totalPages()).isZero();
    }

    @Test
    void toPageable_dungSortCuaClientKhiNamTrongWhitelist() {
        ListQuery query = new ListQuery();
        query.setSort("airline_name,desc");

        Pageable pageable = PageMapper.toPageable(query, "airline_code", "airline_code", "airline_name");

        assertThat(pageable.getSort().getOrderFor("airline_name")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("airline_name").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void toPageable_boQuaSortNgoaiWhitelistDungSortMacDinh() {
        ListQuery query = new ListQuery();
        query.setSort("password_hash,asc");   // không nằm trong whitelist

        Pageable pageable = PageMapper.toPageable(query, "airline_code", "airline_code", "airline_name");

        assertThat(pageable.getSort().getOrderFor("airline_code")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("password_hash")).isNull();
    }
}
