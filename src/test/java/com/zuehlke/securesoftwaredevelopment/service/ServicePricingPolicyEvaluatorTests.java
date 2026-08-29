package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Person;
import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.ServiceStatus;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ServicePricingPolicyEvaluatorTests {
    private final ServicePricingPolicyEvaluator evaluator = new ServicePricingPolicyEvaluator();

    @Test
    void choosesTierFromCompletedServiceHistory() {
        assertThat(ServicePricingPolicyEvaluator.PricingTier.fromCompletedServices(0))
                .isEqualTo(ServicePricingPolicyEvaluator.PricingTier.BRONZE);
        assertThat(ServicePricingPolicyEvaluator.PricingTier.fromCompletedServices(3))
                .isEqualTo(ServicePricingPolicyEvaluator.PricingTier.SILVER);
        assertThat(ServicePricingPolicyEvaluator.PricingTier.fromCompletedServices(6))
                .isEqualTo(ServicePricingPolicyEvaluator.PricingTier.GOLD);
    }

    @Test
    void exposesThePolicyResourceUsedForHistoricalPricingMetadata() {
        assertThat(evaluator.policyTierForCompletedServices(0)).isEqualTo("BRONZE");
        assertThat(evaluator.policyResourceForCompletedServices(0))
                .isEqualTo("classpath:pricing/bronze.spel");
        assertThat(evaluator.policyTierForCompletedServices(3)).isEqualTo("SILVER");
        assertThat(evaluator.policyResourceForCompletedServices(6))
                .isEqualTo("classpath:pricing/gold.spel");
    }

    @Test
    void bronzePolicyUsesPersistedPartnerCode() {
        ServiceDetails details = details("8000", "2000");
        Service service = service();

        BigDecimal ordinary = evaluator.evaluate(details, service,
                person("NONE"), 0);
        BigDecimal partner = evaluator.evaluate(details, service,
                person("FLEET-10"), 0);

        assertThat(ordinary).isEqualByComparingTo("10000.00");
        assertThat(partner).isEqualByComparingTo("9500.00");
    }

    @Test
    void introductoryBronzeCodeWorksOnlyForFirstThreeCompletedServices() {
        BigDecimal eligible = evaluator.evaluatePreview(
                "BRONZE", new BigDecimal("8000"), new BigDecimal("2000"),
                90, 2, "BRONZE-FIRST3");
        BigDecimal noLongerEligible = evaluator.evaluatePreview(
                "BRONZE", new BigDecimal("8000"), new BigDecimal("2000"),
                90, 3, "BRONZE-FIRST3");
        BigDecimal wrongTier = evaluator.evaluatePreview(
                "SILVER", new BigDecimal("8000"), new BigDecimal("2000"),
                90, 2, "BRONZE-FIRST3");

        assertThat(eligible).isEqualByComparingTo("9500.00");
        assertThat(noLongerEligible).isEqualByComparingTo("10000.00");
        assertThat(wrongTier).isNotEqualByComparingTo("9500.00");
    }

    @Test
    void persistedTextIsInterpolatedBeforeSpelParsing() {
        ServiceDetails details = details("8000", "2000");
        Service service = service();

        BigDecimal ordinary = evaluator.evaluate(details, service,
                person("NONE"), 0);
        BigDecimal injectedCondition = evaluator.evaluate(details, service,
                person("x' == 'x' or 'x"), 0);

        assertThat(ordinary).isEqualByComparingTo("10000.00");
        assertThat(injectedCondition).isEqualByComparingTo("9500.00");
    }

    @Test
    void calculatorUsesTheSameStringRenderedPricingPath() {
        BigDecimal ordinary = evaluator.evaluatePreview(
                "BRONZE", new BigDecimal("8000"), new BigDecimal("2000"),
                90, 0, "NONE");
        BigDecimal injectedCondition = evaluator.evaluatePreview(
                "BRONZE", new BigDecimal("8000"), new BigDecimal("2000"),
                90, 0, "x' == 'x' or 'x");

        assertThat(ordinary).isEqualByComparingTo("10000.00");
        assertThat(injectedCondition).isEqualByComparingTo("9500.00");
    }

    @Test
    void policiesProduceDifferentPricesForSameService() {
        ServiceDetails details = details("20000", "30000");
        Service service = service();
        Person person = person("NONE");

        BigDecimal bronze = evaluator.evaluate(details, service, person, 0);
        BigDecimal silver = evaluator.evaluate(details, service, person, 3);
        BigDecimal gold = evaluator.evaluate(details, service, person, 6);

        assertThat(bronze).isNotEqualByComparingTo(silver);
        assertThat(silver).isNotEqualByComparingTo(gold);
    }

    private ServiceDetails details(String labor, String parts) {
        ServiceDetails details = new ServiceDetails(10);
        ServiceDetails.PerformedService performedService = new ServiceDetails.PerformedService();
        performedService.setLaborPrice(new BigDecimal(labor));

        ServiceDetails.UsedPart usedPart = new ServiceDetails.UsedPart();
        usedPart.setQuantity(BigDecimal.ONE);
        usedPart.setUnitPrice(new BigDecimal(parts));
        usedPart.setLineTotal(new BigDecimal(parts));
        performedService.setUsedParts(Collections.singletonList(usedPart));
        details.setPerformedServices(Collections.singletonList(performedService));
        return details;
    }

    private Person person(String partnerCode) {
        return new Person(1, "Test", "Customer", "1", "Address", partnerCode);
    }

    private Service service() {
        return new Service(10, 1, LocalDate.of(2026, 8, 23), LocalTime.of(10, 0),
                "Honda Civic", "Maintenance", ServiceStatus.IN_PROGRESS, "tech", 120,
                (LocalDateTime) null, null);
    }
}
