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

    @Bean("ldapAuthenticationProvider")
    public AuthenticationProvider ldapAuthenticationProvider(BaseLdapPathContextSource contextSource) {
        FilterBasedLdapUserSearch userSearch = new FilterBasedLdapUserSearch(
                "ou=people",
                "(mail={0})",
                contextSource
        );
        userSearch.setSearchSubtree(true);

        BindAuthenticator authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserSearch(userSearch);

        DefaultLdapAuthoritiesPopulator authoritiesPopulator =
                new DefaultLdapAuthoritiesPopulator(contextSource, "ou=groups");
        authoritiesPopulator.setGroupSearchFilter("(member={0})");
        authoritiesPopulator.setGroupRoleAttribute("cn");
        authoritiesPopulator.setRolePrefix("ROLE_");
        authoritiesPopulator.setSearchSubtree(true);

        return new LdapAuthenticationProvider(authenticator, authoritiesPopulator);
    }
}
