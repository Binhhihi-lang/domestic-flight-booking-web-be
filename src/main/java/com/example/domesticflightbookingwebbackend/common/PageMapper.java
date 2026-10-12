package com.example.domesticflightbookingwebbackend.common;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;

/** Chuyển {@link Page} của Spring Data thành {@link PageData} theo hợp đồng API (mục 1.6). */
public final class PageMapper {

    private PageMapper() {
    }

    public static <T> PageData<T> toPageData(Page<T> page) {
        return new PageData<>(
                page.getContent(),
                new PageData.Pagination(
                        page.getNumber() + 1,          // Spring 0-based → API 1-based
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages()
                )
        );
    }

    /** Như trên, nhưng map từng phần tử Entity → DTO. */
    public static <E, D> PageData<D> toPageData(Page<E> page, Function<E, D> mapper) {
        List<D> items = page.getContent().stream().map(mapper).toList();
        return new PageData<>(
                items,
                new PageData.Pagination(
                        page.getNumber() + 1,
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages()
                )
        );
    }

    /**
     * Dựng {@link Pageable} từ {@link ListQuery}, dùng sort mặc định nếu sort
     * của client không nằm trong whitelist (BR-LIST-03: bỏ qua, không báo lỗi).
     *
     * <p>Whitelist dùng tên field theo HỢP ĐỒNG API (snake_case, ví dụ
     * {@code created_at}), còn Spring Data cần tên property entity (camelCase,
     * {@code createdAt}) — nên field hợp lệ được chuyển đổi trước khi dựng Sort.
     * {@code defaultSortField} truyền thẳng tên property entity.
     */
    public static Pageable toPageable(ListQuery query, String defaultSortField, String... allowedSortFields) {
        int page = query.getPageOrDefault() - 1;   // API 1-based → Spring 0-based
        int size = query.getSizeOrDefault();

        if (query.isSortAllowed(allowedSortFields)) {
            Sort.Direction direction = "desc".equals(query.getSortDirection())
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            return PageRequest.of(page, size, Sort.by(direction, toEntityProperty(query.getSortField())));
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, defaultSortField));
    }

    /** {@code created_at} → {@code createdAt} (tên field API → property entity). */
    private static String toEntityProperty(String apiField) {
        StringBuilder sb = new StringBuilder(apiField.length());
        boolean upperNext = false;
        for (char c : apiField.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else {
                sb.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
        }
        return sb.toString();
    }
}
