package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Person;
import com.zuehlke.securesoftwaredevelopment.domain.Service;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceDetails;
import org.springframework.core.io.ClassPathResource;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
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
        return evaluate(tier,
                pricing.laborPrice,
                pricing.partsPrice,
                service == null || service.getEstimatedDurationMinutes() == null
                        ? 0 : service.getEstimatedDurationMinutes(),
                completedServices,
                customer == null ? null : customer.getPartnerCode());
    }

    public BigDecimal evaluatePreview(String tierName,
                                      BigDecimal laborPrice,
                                      BigDecimal partsPrice,
                                      int estimatedDurationMinutes,
                                      int completedServices,
                                      String partnerCode) {
        PricingTier tier;
        try {
            tier = PricingTier.valueOf(tierName == null ? "" : tierName.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown loyalty tier");
        }
        return evaluate(tier, laborPrice, partsPrice, estimatedDurationMinutes,
                completedServices, partnerCode);
    }

    private BigDecimal evaluate(PricingTier tier,
                                BigDecimal laborPrice,
                                BigDecimal partsPrice,
                                int estimatedDurationMinutes,
                                int completedServices,
                                String partnerCode) {
        BigDecimal safeLaborPrice = nonNegative(laborPrice, "Labor price");
        BigDecimal safePartsPrice = nonNegative(partsPrice, "Parts price");
        if (estimatedDurationMinutes < 0) {
            throw new IllegalArgumentException("Estimated duration cannot be negative");
        }
        if (completedServices < 0) {
            throw new IllegalArgumentException("Completed services cannot be negative");
        }

        BigDecimal basePrice = safeLaborPrice.add(safePartsPrice);
        String template = loadTemplate(tier);
        String expressionText = renderExpression(
                template,
                basePrice,
                safeLaborPrice,
                safePartsPrice,
                estimatedDurationMinutes,
                completedServices,
                partnerCode
        );

        Expression expression = parser.parseExpression(expressionText);
        Object result = expression.getValue();
        BigDecimal finalPrice = monetaryValue(result);
        if (finalPrice.signum() < 0) {
            throw new IllegalStateException("Pricing policy returned a negative price");
        }
        return finalPrice.setScale(2, RoundingMode.HALF_UP);
    }

    String renderExpression(String template,
                            BigDecimal basePrice,
                            BigDecimal laborPrice,
                            BigDecimal partsPrice,
                            int estimatedDurationMinutes,
                            int completedServices,
                            String partnerCode) {
        String persistedCode = partnerCode == null ? "" : partnerCode;

        // Pricing resources are templates. Their values are deliberately materialized
        // by string replacement before the resulting expression is parsed.
        return template
                .replace("${basePrice}", basePrice.toPlainString())
                .replace("${laborPrice}", laborPrice.toPlainString())
                .replace("${partsPrice}", partsPrice.toPlainString())
                .replace("${estimatedDurationMinutes}", Integer.toString(estimatedDurationMinutes))
                .replace("${completedServices}", Integer.toString(completedServices))
                .replace("${partnerCode}", "'" + persistedCode + "'");
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
            }
        }
        return new PricingValues(laborPrice, partsPrice);
    }

    private BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be zero or greater");
        }
        return value;
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

        private PricingValues(BigDecimal laborPrice, BigDecimal partsPrice) {
            this.laborPrice = laborPrice;
            this.partsPrice = partsPrice;
        }
    }
}
