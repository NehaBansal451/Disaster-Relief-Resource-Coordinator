package com.disasterrelief.backend.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PageQueryTest {

    private static final Map<String, String> SORTS = Map.of("name", "lower(name)", "startedAt", "started_at");

    @Test
    void usesDefaultsWhenNothingIsGiven() {
        PageQuery q = PageQuery.parse(null, null, null, SORTS, "startedAt", true);
        assertEquals(0, q.page());
        assertEquals(20, q.size());
        assertEquals("started_at DESC, id ASC", q.orderBySql());
        assertEquals(0, q.offset());
    }

    @Test
    void parsesFieldAndDirectionAndComputesOffset() {
        PageQuery q = PageQuery.parse(3, 50, "name,desc", SORTS, "startedAt", true);
        assertEquals("lower(name) DESC, id ASC", q.orderBySql());
        assertEquals(150, q.offset());
    }

    @Test
    void sortWithoutDirectionIsAscending() {
        assertEquals("lower(name) ASC, id ASC", PageQuery.parse(0, 10, "name", SORTS, "startedAt", true).orderBySql());
    }

    @Test
    void acceptsBoundarySizes() {
        assertEquals(1, PageQuery.parse(0, 1, null, SORTS, "name", false).size());
        assertEquals(100, PageQuery.parse(0, 100, null, SORTS, "name", false).size());
    }

    @Test
    void rejectsOutOfRangePageAndSize() {
        ApiException ex = assertThrows(ApiException.class, () -> PageQuery.parse(-1, 0, null, SORTS, "name", false));
        assertEquals("VALIDATION_ERROR", ex.code());
        assertEquals(2, ex.errors().size());
        assertThrows(ApiException.class, () -> PageQuery.parse(0, 101, null, SORTS, "name", false));
    }

    @Test
    void rejectsUnknownSortFieldSoUserInputNeverReachesSql() {
        ApiException ex = assertThrows(ApiException.class,
                () -> PageQuery.parse(0, 10, "name; DROP TABLE users", SORTS, "name", false));
        assertEquals("sort", ex.errors().get(0).field());
    }

    @Test
    void rejectsBadDirectionAndTooManyParts() {
        assertThrows(ApiException.class, () -> PageQuery.parse(0, 10, "name,sideways", SORTS, "name", false));
        assertThrows(ApiException.class, () -> PageQuery.parse(0, 10, "name,asc,x", SORTS, "name", false));
    }

    @Test
    void pageResponseComputesTotalPages() {
        PageQuery q = PageQuery.parse(0, 20, null, SORTS, "name", false);
        assertEquals(0, PageResponse.of(java.util.List.of(), q, 0).totalPages());
        assertEquals(1, PageResponse.of(java.util.List.of(), q, 20).totalPages());
        assertEquals(2, PageResponse.of(java.util.List.of(), q, 21).totalPages());
        assertTrue(PageResponse.of(java.util.List.of(), q, 21).content().isEmpty());
    }
}
