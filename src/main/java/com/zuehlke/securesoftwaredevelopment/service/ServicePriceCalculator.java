package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ServicePriceCalculator {
    private static final int MONEY_SCALE = 2;

    public ServiceDetails calculate(ServiceDetails details) {
        BigDecimal finalTotal = BigDecimal.ZERO;

        for (ServiceDetails.PerformedService performedService : details.getPerformedServices()) {
            BigDecimal laborPrice = money(performedService.getLaborPrice(), "Labor price");
            BigDecimal serviceTotal = laborPrice;
            performedService.setLaborPrice(laborPrice);

            for (ServiceDetails.UsedPart usedPart : performedService.getUsedParts()) {
                BigDecimal quantity = positive(usedPart.getQuantity(), "Part quantity");
                BigDecimal unitPrice = money(usedPart.getUnitPrice(), "Part unit price");
                BigDecimal lineTotal = quantity.multiply(unitPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

                usedPart.setQuantity(quantity);
                usedPart.setUnitPrice(unitPrice);
                usedPart.setLineTotal(lineTotal);
                serviceTotal = serviceTotal.add(lineTotal);
            }

            serviceTotal = serviceTotal.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            performedService.setTotalPrice(serviceTotal);
            finalTotal = finalTotal.add(serviceTotal);
        }

        details.setTotalPrice(finalTotal.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        return details;
    }

    private BigDecimal money(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be zero or greater");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
        return value.stripTrailingZeros();
    }
}
