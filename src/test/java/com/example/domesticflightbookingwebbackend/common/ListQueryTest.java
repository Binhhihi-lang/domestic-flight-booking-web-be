package com.example.domesticflightbookingwebbackend.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListQueryTest {

    @Test
    void macDinhPage1Size20() {
        ListQuery query = new ListQuery();

        assertThat(query.getPageOrDefault()).isEqualTo(1);
        assertThat(query.getSizeOrDefault()).isEqualTo(20);
    }

    @Test
    void pageNhoHon1TraVeMacDinh() {
        ListQuery query = new ListQuery();
        query.setPage(0);

        assertThat(query.getPageOrDefault()).isEqualTo(1);
    }

    @Test
    void sizeBiKepToiDa100() {
        ListQuery query = new ListQuery();
        query.setSize(500);

        assertThat(query.getSizeOrDefault()).isEqualTo(100);
    }

    @Test
    void keywordRongTraVeNull() {
        ListQuery query = new ListQuery();
        query.setKeyword("   ");

        assertThat(query.getKeywordOrNull()).isNull();
    }

    @Test
    void keywordDuocTrim() {
        ListQuery query = new ListQuery();
        query.setKeyword("  vietnam  ");

        assertThat(query.getKeywordOrNull()).isEqualTo("vietnam");
    }

    @Test
    void parseSortFieldVaDirection() {
        ListQuery query = new ListQuery();
        query.setSort("airline_name,desc");

        assertThat(query.getSortField()).isEqualTo("airline_name");
        assertThat(query.getSortDirection()).isEqualTo("desc");
    }

    @Test
    void sortSaiDinhDangBiCoiLaKhongHopLe() {
        ListQuery query = new ListQuery();
        query.setSort("airline_name");

        assertThat(query.getSortField()).isNull();
        assertThat(query.isSortAllowed("airline_name")).isFalse();
    }

    @Test
    void isSortAllowed_kiemTraWhitelist() {
        ListQuery query = new ListQuery();
        query.setSort("airline_code,asc");

        assertThat(query.isSortAllowed("airline_code", "airline_name")).isTrue();
        assertThat(query.isSortAllowed("city_code")).isFalse();
    }
}
