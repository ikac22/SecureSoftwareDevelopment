package com.zuehlke.securesoftwaredevelopment.repository;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class SafePartCatalogQueryBuilder {
    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> STABLE_FIELDS = new HashSet<>(Arrays.asList(
            "name", "partNumber", "manufacturer", "unit"
    ));

    /** Reference implementation used to demonstrate the mitigation for the vulnerable baseline. */
    public Query build(Map<String, Object> filters, int page, int requestedPageSize) {
        if (page < 0 || requestedPageSize <= 0) {
            throw new IllegalArgumentException("Page and page size must be positive");
        }

        int pageSize = Math.min(requestedPageSize, MAX_PAGE_SIZE);
        Query query = new Query().skip((long) page * pageSize).limit(pageSize);
        if (filters == null) {
            return query;
        }

        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            if (!allowedField(filter.getKey())) {
                throw new IllegalArgumentException("Unsupported part search field");
            }
            Object value = filter.getValue();
            if (value == null || value instanceof Map || value instanceof Iterable
                    || value.getClass().isArray()) {
                throw new IllegalArgumentException("Part search values must be scalar");
            }
            query.addCriteria(Criteria.where(filter.getKey()).is(value));
        }
        return query;
    }

    private boolean allowedField(String field) {
        return STABLE_FIELDS.contains(field)
                || (field != null && field.matches("attributes\\.[A-Za-z][A-Za-z0-9_-]{0,63}"));
    }
}
