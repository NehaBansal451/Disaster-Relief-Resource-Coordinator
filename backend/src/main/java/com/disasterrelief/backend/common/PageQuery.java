package com.disasterrelief.backend.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validated paging and sorting from ?page=0&size=20&sort=field,desc.
 * The sort field is looked up in a whitelist that maps API names to SQL expressions, so user input is never
 * concatenated into SQL.
 */
public record PageQuery(int page, int size, String orderBySql) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /**
     * @param allowedSorts API field name to SQL expression, e.g. "startedAt" to "started_at"
     * @param defaultSort  API field name used when {@code sort} is absent
     * @param defaultDesc  direction used when {@code sort} is absent
     */
    public static PageQuery parse(Integer page, Integer size, String sort,
                                  Map<String, String> allowedSorts, String defaultSort, boolean defaultDesc) {
        List<FieldProblem> errors = new ArrayList<>();
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0) {
            errors.add(new FieldProblem("page", "page must be 0 or greater"));
        }
        if (s < 1 || s > MAX_SIZE) {
            errors.add(new FieldProblem("size", "size must be between 1 and " + MAX_SIZE));
        }

        String field = defaultSort;
        boolean desc = defaultDesc;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", -1);
            field = parts[0].trim();
            if (parts.length > 2) {
                errors.add(new FieldProblem("sort", "use sort=field or sort=field,asc|desc"));
            } else if (parts.length == 2) {
                String direction = parts[1].trim().toLowerCase();
                if (direction.equals("desc")) {
                    desc = true;
                } else if (direction.equals("asc")) {
                    desc = false;
                } else {
                    errors.add(new FieldProblem("sort", "direction must be asc or desc"));
                }
            } else {
                desc = false;
            }
        }
        if (!allowedSorts.containsKey(field)) {
            errors.add(new FieldProblem("sort", "sort field must be one of " + allowedSorts.keySet()));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        String orderBy = allowedSorts.get(field) + (desc ? " DESC" : " ASC") + ", id ASC";
        return new PageQuery(p, s, orderBy);
    }

    public long offset() {
        return (long) page * size;
    }
}
