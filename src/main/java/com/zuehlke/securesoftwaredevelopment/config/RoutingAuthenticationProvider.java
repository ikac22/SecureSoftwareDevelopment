package com.zuehlke.securesoftwaredevelopment.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class RoutingAuthenticationProvider implements AuthenticationProvider {

    static final String INTERNAL_EMAIL_SUFFIX = "@securecar.test";

    private final DatabaseAuthenticationProvider databaseAuthenticationProvider;
    private final AuthenticationProvider ldapAuthenticationProvider;

    public RoutingAuthenticationProvider(
            DatabaseAuthenticationProvider databaseAuthenticationProvider,
            @Qualifier("ldapAuthenticationProvider") AuthenticationProvider ldapAuthenticationProvider) {
        this.databaseAuthenticationProvider = databaseAuthenticationProvider;
        this.ldapAuthenticationProvider = ldapAuthenticationProvider;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String identifier = authentication.getName().trim();

        UsernamePasswordAuthenticationToken delegatedAuthentication =
                new UsernamePasswordAuthenticationToken(identifier, authentication.getCredentials());
        delegatedAuthentication.setDetails(authentication.getDetails());

        if (isInternalEmail(identifier)) {
            return ldapAuthenticationProvider.authenticate(delegatedAuthentication);
        }

        return databaseAuthenticationProvider.authenticate(delegatedAuthentication);
    }

    boolean isInternalEmail(String identifier) {
        return identifier.toLowerCase(Locale.ROOT).endsWith(INTERNAL_EMAIL_SUFFIX);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
