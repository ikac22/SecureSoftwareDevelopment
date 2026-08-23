package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Person;
import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import org.springframework.core.io.ClassPathResource;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

@Component
public class ServicePricingPolicyEvaluator {
    private final SpelExpressionParser parser = new SpelExpressionParser();

    public BigDecimal evaluate(ServiceDetails details, Service service, Person customer,
                               int completedServices) {
        PricingValues pricing = pricingValues(details);
        PricingTier tier = PricingTier.fromCompletedServices(completedServices);
        String template = loadTemplate(tier);
        String expressionText = renderExpression(template, customer == null ? null : customer.getPartnerCode());

        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("basePrice", pricing.basePrice);
        context.setVariable("laborPrice", pricing.laborPrice);
        context.setVariable("partsPrice", pricing.partsPrice);
        context.setVariable("partsCount", pricing.partsCount);
        context.setVariable("completedServices", completedServices);
        context.setVariable("carModel", service == null ? null : service.getCarModel());
        context.setVariable("estimatedDurationMinutes",
                service == null ? null : service.getEstimatedDurationMinutes());

        Expression expression = parser.parseExpression(expressionText);
        Object result = expression.getValue(context);
        BigDecimal finalPrice = monetaryValue(result);
        if (finalPrice.signum() < 0) {
            throw new IllegalStateException("Pricing policy returned a negative price");
        }
        return finalPrice.setScale(2, RoundingMode.HALF_UP);
    }

    String renderExpression(String template, String partnerCode) {
        String persistedCode = partnerCode == null ? "" : partnerCode;
        String partnerCondition = "'" + persistedCode + "' == 'FLEET-10'";
        return template.replace("${partnerCondition}", partnerCondition);
    }

    private String loadTemplate(PricingTier tier) {
        ClassPathResource resource = new ClassPathResource(
                "pricing/" + tier.name().toLowerCase() + ".spel");
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load pricing policy " + tier, exception);
        }
    }

    private PricingValues pricingValues(ServiceDetails details) {
        BigDecimal laborPrice = BigDecimal.ZERO;
        BigDecimal partsPrice = BigDecimal.ZERO;
        int partsCount = 0;

        for (ServiceDetails.PerformedService performedService : details.getPerformedServices()) {
            if (performedService.getLaborPrice() != null) {
                laborPrice = laborPrice.add(performedService.getLaborPrice());
            }
            for (ServiceDetails.UsedPart usedPart : performedService.getUsedParts()) {
                if (usedPart.getLineTotal() != null) {
                    partsPrice = partsPrice.add(usedPart.getLineTotal());
                } else if (usedPart.getQuantity() != null && usedPart.getUnitPrice() != null) {
                    partsPrice = partsPrice.add(usedPart.getQuantity().multiply(usedPart.getUnitPrice()));
                }
                partsCount++;
            }
        }
        return new PricingValues(laborPrice, partsPrice, partsCount);
    }

    private BigDecimal monetaryValue(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        throw new IllegalStateException("Pricing policy must return a numeric value");
    }

    enum PricingTier {
        BRONZE,
        SILVER,
        GOLD;

        static PricingTier fromCompletedServices(int completedServices) {
            if (completedServices >= 6) {
                return GOLD;
            }
            if (completedServices >= 3) {
                return SILVER;
            }
            return BRONZE;
        }
    }

    private static class PricingValues {
        private final BigDecimal laborPrice;
        private final BigDecimal partsPrice;
        private final BigDecimal basePrice;
        private final int partsCount;

        private PricingValues(BigDecimal laborPrice, BigDecimal partsPrice, int partsCount) {
            this.laborPrice = laborPrice;
            this.partsPrice = partsPrice;
            this.basePrice = laborPrice.add(partsPrice);
            this.partsCount = partsCount;
        }
    }
}
