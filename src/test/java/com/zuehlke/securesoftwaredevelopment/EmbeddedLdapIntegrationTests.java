package com.zuehlke.securesoftwaredevelopment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class EmbeddedLdapIntegrationTests {

    @Autowired
    private LdapTemplate ldapTemplate;

    @Test
    void loadsSeededEmployees() {
        List<String> technicianEmails = ldapTemplate.search(
                "ou=people",
                "(mail=marko.markovic@securecar.test)",
                (AttributesMapper<String>) attributes -> attributes.get("mail").get().toString()
        );

        List<String> managerEmails = ldapTemplate.search(
                "ou=people",
                "(mail=ana.anic@securecar.test)",
                (AttributesMapper<String>) attributes -> attributes.get("mail").get().toString()
        );

        List<String> employeeEmails = ldapTemplate.search(
                "ou=people",
                "(mail=nikola.nikolic@securecar.test)",
                (AttributesMapper<String>) attributes -> attributes.get("mail").get().toString()
        );

        assertEquals(1, technicianEmails.size());
        assertEquals("marko.markovic@securecar.test", technicianEmails.get(0));
        assertEquals(1, managerEmails.size());
        assertEquals("ana.anic@securecar.test", managerEmails.get(0));
        assertEquals(1, employeeEmails.size());
        assertEquals("nikola.nikolic@securecar.test", employeeEmails.get(0));
    }

    @Test
    void loadsSeededServiceGroups() {
        List<String> technicianGroups = ldapTemplate.search(
                "ou=groups",
                "(&(objectClass=groupOfNames)(member=uid=marko.markovic,ou=people,dc=securecar,dc=test))",
                (AttributesMapper<String>) attributes -> attributes.get("cn").get().toString()
        );

        List<String> managerGroups = ldapTemplate.search(
                "ou=groups",
                "(&(objectClass=groupOfNames)(member=uid=ana.anic,ou=people,dc=securecar,dc=test))",
                (AttributesMapper<String>) attributes -> attributes.get("cn").get().toString()
        );

        assertEquals(1, technicianGroups.size());
        assertEquals("SERVICE_TECHNICIANS", technicianGroups.get(0));
        assertEquals(1, managerGroups.size());
        assertEquals("SERVICE_MANAGERS", managerGroups.get(0));
    }
}
