package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceTypeRepository extends MongoRepository<ServiceType, String> {
    Optional<ServiceType> findByNormalizedName(String normalizedName);

    List<ServiceType> findByNameContainingIgnoreCaseOrderByName(String name);
}
