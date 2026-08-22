package com.zuehlke.securesoftwaredevelopment;

import com.zuehlke.securesoftwaredevelopment.config.LDAPAuthorities;
import com.zuehlke.securesoftwaredevelopment.config.RoutingAuthenticationProvider;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AuthenticationIntegrationTests {

    @Autowired
    private RoutingAuthenticationProvider authenticationProvider;

    @Test
    void authenticatesDatabaseUserByUsername() {
        Authentication authentication = authenticate("bruce", "wayne");

        User principal = (User) authentication.getPrincipal();
        assertEquals("bruce", principal.getUsername());
        assertTrue(authentication.getAuthorities().contains(new SimpleGrantedAuthority("CAR_LIST_VIEW")));
    }

    @Test
    void authenticatesDatabaseUserByExternalEmailAndKeepsCanonicalUsername() {
        Authentication authentication = authenticate("bruce@example.com", "wayne");

        User principal = (User) authentication.getPrincipal();
        assertEquals("bruce", principal.getUsername());
        assertTrue(authentication.getAuthorities().contains(new SimpleGrantedAuthority("CAR_LIST_VIEW")));
    }

    @Test
    void authenticatesTechnicianFromLdap() {
        Authentication authentication = authenticate("marko.markovic@securecar.test", "technician123");

        assertTrue(authentication.isAuthenticated());
        assertTrue(authentication.getAuthorities().contains(new SimpleGrantedAuthority(LDAPAuthorities.TECHNICIAN)));
        assertFalse(authentication.getAuthorities().contains(new SimpleGrantedAuthority(LDAPAuthorities.SERVICE_MANAGER)));
    }

    @Test
    void authenticatesServiceManagerFromLdap() {
        Authentication authentication = authenticate("ana.anic@securecar.test", "manager123");

        assertTrue(authentication.isAuthenticated());
        assertTrue(authentication.getAuthorities().contains(new SimpleGrantedAuthority(LDAPAuthorities.SERVICE_MANAGER)));
    }

    @Test
    void ldapEmployeeWithoutServiceGroupGetsNoServiceAuthorities() {
        Authentication authentication = authenticate("nikola.nikolic@securecar.test", "employee123");

        assertTrue(authentication.isAuthenticated());
        assertFalse(authentication.getAuthorities().contains(new SimpleGrantedAuthority(LDAPAuthorities.TECHNICIAN)));
        assertFalse(authentication.getAuthorities().contains(new SimpleGrantedAuthority(LDAPAuthorities.SERVICE_MANAGER)));
    }

    @Test
    void rejectsWrongLdapPassword() {
        assertThrows(BadCredentialsException.class,
                () -> authenticate("marko.markovic@securecar.test", "wrong-password"));
    }

    private Authentication authenticate(String identifier, String password) {
        return authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken(identifier, password)
        );
    }
}
