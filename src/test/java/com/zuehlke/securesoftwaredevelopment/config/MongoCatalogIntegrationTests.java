package com.zuehlke.securesoftwaredevelopment.config;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import com.zuehlke.securesoftwaredevelopment.repository.PartCatalogRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceDetailsRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ServiceTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
class MongoCatalogIntegrationTests {
    @Autowired
    private PartCatalogRepository partCatalogRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private ServiceDetailsRepository serviceDetailsRepository;

    @Autowired
    private MongoCatalogSeeder mongoCatalogSeeder;

    @Test
    void embeddedMongoStartsAndSeedDataIsIdempotent() {
        assertThat(partCatalogRepository.count()).isEqualTo(5);
        assertThat(serviceTypeRepository.count()).isEqualTo(4);
        assertThat(serviceDetailsRepository.count()).isEqualTo(2);

        ServiceDetails inProgress = serviceDetailsRepository.findByServiceId(2).orElseThrow(AssertionError::new);
        ServiceDetails completed = serviceDetailsRepository.findByServiceId(3).orElseThrow(AssertionError::new);
        assertThat(inProgress.getTotalPrice()).isEqualByComparingTo("11400.00");
        assertThat(completed.getTotalPrice()).isEqualByComparingTo("10000.00");

        mongoCatalogSeeder.run(mock(ApplicationArguments.class));

        assertThat(partCatalogRepository.count()).isEqualTo(5);
        assertThat(serviceTypeRepository.count()).isEqualTo(4);
        assertThat(serviceDetailsRepository.count()).isEqualTo(2);
    }
}
