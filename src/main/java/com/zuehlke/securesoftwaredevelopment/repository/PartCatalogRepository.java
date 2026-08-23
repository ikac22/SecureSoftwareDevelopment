package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.PartCatalogItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PartCatalogRepository extends MongoRepository<PartCatalogItem, String> {
    Optional<PartCatalogItem> findByNormalizedKey(String normalizedKey);

    List<PartCatalogItem> findByNameContainingIgnoreCaseOrderByName(String name);
}
