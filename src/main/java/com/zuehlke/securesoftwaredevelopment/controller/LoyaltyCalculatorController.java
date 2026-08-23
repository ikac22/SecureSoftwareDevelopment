package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.service.ServicePricingPolicyEvaluator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class LoyaltyCalculatorController {
    private static final Pattern EXPRESSION_ERROR_CODE = Pattern.compile("EL\\d{4}E");

    private final ServicePricingPolicyEvaluator pricingPolicyEvaluator;

    public LoyaltyCalculatorController(ServicePricingPolicyEvaluator pricingPolicyEvaluator) {
        this.pricingPolicyEvaluator = pricingPolicyEvaluator;
    }

    @PostMapping("/loyalty-calculator")
    public ResponseEntity<Map<String, Object>> calculate(
            @RequestParam String tier,
            @RequestParam BigDecimal laborPrice,
            @RequestParam BigDecimal partsPrice,
            @RequestParam int estimatedDurationMinutes,
            @RequestParam int completedServices,
            @RequestParam(required = false, defaultValue = "") String partnerCode,
            Authentication authentication) {
        requireCustomer(authentication);

        try {
            BigDecimal standardPrice = laborPrice.add(partsPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal discountedPrice = pricingPolicyEvaluator.evaluatePreview(
                    tier,
                    laborPrice,
                    partsPrice,
                    estimatedDurationMinutes,
                    completedServices,
                    partnerCode
            );
            BigDecimal discount = standardPrice.subtract(discountedPrice)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("standardPrice", standardPrice);
            result.put("discountedPrice", discountedPrice);
            result.put("discount", discount);
            return ResponseEntity.ok(result);
        } catch (RuntimeException exception) {
            String errorCode = expressionErrorCode(exception);
            if (errorCode == null) {
                throw exception;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("errorCode", errorCode);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }

    private void requireCustomer(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Loyalty calculator is available to customers only");
        }
    }

    private String expressionErrorCode(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                Matcher matcher = EXPRESSION_ERROR_CODE.matcher(message);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
            current = current.getCause();
        }
        return null;
    }
}
