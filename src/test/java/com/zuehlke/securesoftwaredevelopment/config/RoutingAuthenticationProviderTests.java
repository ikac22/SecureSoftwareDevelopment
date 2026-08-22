package com.zuehlke.securesoftwaredevelopment.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingAuthenticationProviderTests {

    @Test
    void internalEmailUsesOnlyLdapProvider() {
        DatabaseAuthenticationProvider databaseProvider = mock(DatabaseAuthenticationProvider.class);
        AuthenticationProvider ldapProvider = mock(AuthenticationProvider.class);
        RoutingAuthenticationProvider router = new RoutingAuthenticationProvider(databaseProvider, ldapProvider);

        when(ldapProvider.authenticate(any())).thenThrow(new BadCredentialsException("LDAP failed"));

        assertThrows(BadCredentialsException.class,
                () -> router.authenticate(token("marko.markovic@securecar.test", "wrong")));

        verify(ldapProvider).authenticate(any());
        verify(databaseProvider, never()).authenticate(any());
    }

    @Test
    void externalEmailUsesOnlyDatabaseProvider() {
        DatabaseAuthenticationProvider databaseProvider = mock(DatabaseAuthenticationProvider.class);
        AuthenticationProvider ldapProvider = mock(AuthenticationProvider.class);
        RoutingAuthenticationProvider router = new RoutingAuthenticationProvider(databaseProvider, ldapProvider);

        when(databaseProvider.authenticate(any())).thenThrow(new BadCredentialsException("Database failed"));

        assertThrows(BadCredentialsException.class,
                () -> router.authenticate(token("bruce@example.com", "wrong")));

        verify(databaseProvider).authenticate(any());
        verify(ldapProvider, never()).authenticate(any());
    }

    @Test
    void usernameUsesOnlyDatabaseProvider() {
        DatabaseAuthenticationProvider databaseProvider = mock(DatabaseAuthenticationProvider.class);
        AuthenticationProvider ldapProvider = mock(AuthenticationProvider.class);
        RoutingAuthenticationProvider router = new RoutingAuthenticationProvider(databaseProvider, ldapProvider);

        when(databaseProvider.authenticate(any())).thenThrow(new BadCredentialsException("Database failed"));

        assertThrows(BadCredentialsException.class,
                () -> router.authenticate(token("bruce", "wrong")));

        verify(databaseProvider).authenticate(any());
        verify(ldapProvider, never()).authenticate(any());
    }

    private UsernamePasswordAuthenticationToken token(String identifier, String password) {
        return new UsernamePasswordAuthenticationToken(identifier, password);
    }
}
