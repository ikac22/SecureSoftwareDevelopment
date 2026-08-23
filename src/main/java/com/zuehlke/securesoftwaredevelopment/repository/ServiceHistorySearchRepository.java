package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class ServiceHistorySearchRepository {
    private final MongoTemplate mongoTemplate;

    public ServiceHistorySearchRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<ServiceDetails> search(int authenticatedCustomerId, Map<String, Object> filters) {
        Map<String, Object> effectiveFilters = filters == null
                ? Collections.emptyMap()
                : filters;

        Document query = new Document(effectiveFilters);
        query.putIfAbsent("customerId", authenticatedCustomerId);
        query.put("completedAt", new Document("$ne", null));

        return mongoTemplate.find(
                new BasicQuery(query),
                ServiceDetails.class
        );
    }
}
