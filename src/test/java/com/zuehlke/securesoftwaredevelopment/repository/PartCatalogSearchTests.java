package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.PartCatalogItem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PartCatalogSearchTests {
    @Test
    void vulnerableSearchPreservesOperatorDocumentFromRequest() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        VulnerablePartCatalogSearch search = new VulnerablePartCatalogSearch(mongoTemplate);
        Map<String, Object> operator = new LinkedHashMap<>();
        operator.put("$ne", null);

        search.search(Collections.singletonMap("name", operator));

        ArgumentCaptor<BasicQuery> queryCaptor = ArgumentCaptor.forClass(BasicQuery.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(PartCatalogItem.class));
        assertThat(queryCaptor.getValue().getQueryObject().toJson()).contains("$ne");
    }

    @Test
    void mitigatedBuilderRejectsOperatorsAndCapsPageSize() {
        SafePartCatalogQueryBuilder builder = new SafePartCatalogQueryBuilder();
        Map<String, Object> operator = new LinkedHashMap<>();
        operator.put("$ne", null);

        assertThatThrownBy(() -> builder.build(
                Collections.singletonMap("name", operator), 0, 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.build(
                Collections.singletonMap("$where", "true"), 0, 10))
                .isInstanceOf(IllegalArgumentException.class);

        Query query = builder.build(Collections.singletonMap("attributes.axle", "FRONT"), 1, 1000);
        assertThat(query.getLimit()).isEqualTo(50);
        assertThat(query.getSkip()).isEqualTo(50);
    }
}
