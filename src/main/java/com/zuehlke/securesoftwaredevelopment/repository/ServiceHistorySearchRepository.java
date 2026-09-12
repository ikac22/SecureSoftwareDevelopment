package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Repository
public class ServiceHistorySearchRepository {
    private final MongoTemplate mongoTemplate;

    public ServiceHistorySearchRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<ServiceDetails> search(int authenticatedCustomerId,
                                       String carModel,
                                       String serviceName,
                                       String partName,
                                       String technician,
                                       boolean showPerformedServices,
                                       boolean showUsedParts) {
        Document match = new Document("customerId", authenticatedCustomerId)
                .append("completedAt", new Document("$ne", null));
        appendTextCriterion(match, "carModel", carModel);
        appendTextCriterion(match, "performedServices.name", serviceName);
        appendTextCriterion(match, "performedServices.usedParts.name", partName);
        appendTextCriterion(match, "technician", technician);

        Document projection = baseProjection();
        if (showPerformedServices || showUsedParts) {
            projection.append("performedServices.name", 1);
        }
        if (showUsedParts) {
            projection.append("performedServices.usedParts", 1);
        }

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

    private void appendTextCriterion(Document match, String field, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isEmpty()) {
            match.append(field, normalized);
        }
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
