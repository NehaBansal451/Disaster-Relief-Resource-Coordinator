package com.disasterrelief.backend.common;

import java.util.List;

/** Standard list envelope from docs/API_CONTRACT.md: {content[], page, size, totalElements, totalPages}. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> content, PageQuery query, long totalElements) {
        int totalPages = (int) Math.ceil(totalElements / (double) query.size());
        return new PageResponse<>(content, query.page(), query.size(), totalElements, totalPages);
    }
}
