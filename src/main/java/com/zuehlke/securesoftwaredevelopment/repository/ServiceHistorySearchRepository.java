package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class ServiceHistorySearchRepository {
    private final MongoTemplate mongoTemplate;

    public ServiceHistorySearchRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<ServiceDetails> search(int authenticatedCustomerId,
                                       Map<String, Object> filters,
                                       Map<String, Object> view) {
        Map<String, Object> effectiveFilters = filters == null
                ? Collections.emptyMap()
                : filters;
        Map<String, Object> effectiveView = view == null
                ? Collections.emptyMap()
                : view;

        Document match = new Document(effectiveFilters);
        match.putIfAbsent("customerId", authenticatedCustomerId);
        match.put("completedAt", new Document("$ne", null));

        Document projection = baseProjection();
        projection.putAll(effectiveView);

        List<Document> pipeline = Arrays.asList(
                new Document("$match", match),
                new Document("$project", projection),
                new Document("$sort", new Document("completedAt", -1))
        );

        List<ServiceDetails> results = new ArrayList<>();
        for (Document document : mongoTemplate
                .getCollection(mongoTemplate.getCollectionName(ServiceDetails.class))
                .aggregate(pipeline)) {
            results.add(mongoTemplate.getConverter().read(ServiceDetails.class, document));
        }
        return results;
    }

    private Document baseProjection() {
        return new Document("serviceId", 1)
                .append("carModel", 1)
                .append("serviceDescription", 1)
                .append("technician", 1)
                .append("serviceDate", 1)
                .append("serviceTime", 1)
                .append("completedAt", 1)
                .append("totalPrice", 1);
    }
}
