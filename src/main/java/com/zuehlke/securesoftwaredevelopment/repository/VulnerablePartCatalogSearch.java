package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.PartCatalogItem;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class VulnerablePartCatalogSearch {
    private final MongoTemplate mongoTemplate;

    public VulnerablePartCatalogSearch(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Intentionally vulnerable teaching example for issue #2. User-controlled nested values
     * are copied into a Mongo query without rejecting query operators.
     */
    public List<PartCatalogItem> search(Map<String, Object> filters) {
        Map<String, Object> effectiveFilters = filters == null ? Collections.emptyMap() : filters;
        return mongoTemplate.find(
                new BasicQuery(new Document(effectiveFilters)),
                PartCatalogItem.class
        );
    }
}
