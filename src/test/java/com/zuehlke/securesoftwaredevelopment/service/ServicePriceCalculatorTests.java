package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServicePriceCalculatorTests {
    private final ServicePriceCalculator calculator = new ServicePriceCalculator();

    @Test
    void calculatesPartServiceAndFinalTotalsWithBigDecimal() {
        ServiceDetails details = new ServiceDetails(1);
        ServiceDetails.PerformedService oilChange = performedService("2500.00",
                part("4.5", "1400.00"), part("1", "1200.00"));
        ServiceDetails.PerformedService inspection = performedService("999.995");
        details.setPerformedServices(Arrays.asList(oilChange, inspection));

        calculator.calculate(details);

        assertThat(oilChange.getUsedParts().get(0).getLineTotal())
                .isEqualByComparingTo("6300.00");
        assertThat(oilChange.getTotalPrice()).isEqualByComparingTo("10000.00");
        assertThat(inspection.getLaborPrice()).isEqualByComparingTo("1000.00");
        assertThat(details.getTotalPrice()).isEqualByComparingTo("11000.00");
    }

    @Test
    void roundsPartLineTotalsHalfUp() {
        ServiceDetails details = new ServiceDetails(1);
        ServiceDetails.PerformedService service = performedService("0", part("3", "0.335"));
        details.setPerformedServices(Collections.singletonList(service));

        calculator.calculate(details);

        assertThat(service.getUsedParts().get(0).getLineTotal()).isEqualByComparingTo("1.01");
        assertThat(details.getTotalPrice()).isEqualByComparingTo("1.01");
    }

    @Test
    void rejectsNonPositiveQuantityAndNegativePrices() {
        ServiceDetails invalidQuantity = new ServiceDetails(1);
        invalidQuantity.setPerformedServices(Collections.singletonList(
                performedService("0", part("0", "10"))));

        assertThatThrownBy(() -> calculator.calculate(invalidQuantity))
                .isInstanceOf(IllegalArgumentException.class);

        ServiceDetails invalidPrice = new ServiceDetails(1);
        invalidPrice.setPerformedServices(Collections.singletonList(performedService("-1")));

        assertThatThrownBy(() -> calculator.calculate(invalidPrice))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ServiceDetails.PerformedService performedService(
            String laborPrice, ServiceDetails.UsedPart... parts) {
        ServiceDetails.PerformedService service = new ServiceDetails.PerformedService();
        service.setLaborPrice(new BigDecimal(laborPrice));
        service.setUsedParts(Arrays.asList(parts));
        return service;
    }

    private ServiceDetails.UsedPart part(String quantity, String unitPrice) {
        ServiceDetails.UsedPart part = new ServiceDetails.UsedPart();
        part.setQuantity(new BigDecimal(quantity));
        part.setUnitPrice(new BigDecimal(unitPrice));
        return part;
    }
}
