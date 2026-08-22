package com.zuehlke.securesoftwaredevelopment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;

@Configuration
public class LDAPAuthenticationConfig {

    private static final String PEOPLE_SEARCH_BASE = "ou=people,dc=securecar,dc=test";
    private static final String GROUPS_SEARCH_BASE = "ou=groups,dc=securecar,dc=test";

    @Bean("ldapAuthenticationProvider")
    public AuthenticationProvider ldapAuthenticationProvider(BaseLdapPathContextSource contextSource) {
        FilterBasedLdapUserSearch userSearch = new FilterBasedLdapUserSearch(
                PEOPLE_SEARCH_BASE,
                "(mail={0})",
                contextSource
        );
        userSearch.setSearchSubtree(true);

        BindAuthenticator authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserSearch(userSearch);

        DefaultLdapAuthoritiesPopulator authoritiesPopulator =
                new DefaultLdapAuthoritiesPopulator(contextSource, GROUPS_SEARCH_BASE);
        authoritiesPopulator.setGroupSearchFilter("(member={0})");
        authoritiesPopulator.setGroupRoleAttribute("cn");
        authoritiesPopulator.setRolePrefix("ROLE_");
        authoritiesPopulator.setSearchSubtree(true);

        return new LdapAuthenticationProvider(authenticator, authoritiesPopulator);
    }
}
