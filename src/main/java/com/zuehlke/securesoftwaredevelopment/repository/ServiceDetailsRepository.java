package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ServiceDetailsRepository extends MongoRepository<ServiceDetails, String> {
    Optional<ServiceDetails> findByServiceId(Integer serviceId);
}
