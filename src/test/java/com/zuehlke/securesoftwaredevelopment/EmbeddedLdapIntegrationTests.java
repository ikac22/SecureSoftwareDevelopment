package com.zuehlke.securesoftwaredevelopment;

import com.zuehlke.securesoftwaredevelopment.domain.Technician;
import com.zuehlke.securesoftwaredevelopment.service.TechnicianDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid}")
class EmbeddedLdapIntegrationTests {

    private static final String PEOPLE_SEARCH_BASE = "ou=people,dc=securecar,dc=test";
    private static final String GROUPS_SEARCH_BASE = "ou=groups,dc=securecar,dc=test";

    @Autowired
    private LdapTemplate ldapTemplate;

    @Autowired
    private TechnicianDirectory technicianDirectory;

    @Test
    void loadsSeededEmployees() {
        List<String> technicianEmails = ldapTemplate.search(
                PEOPLE_SEARCH_BASE,
                "(mail=marko.markovic@securecar.test)",
                (AttributesMapper<String>) attributes -> attributes.get("mail").get().toString()
        );

        List<String> managerEmails = ldapTemplate.search(
                PEOPLE_SEARCH_BASE,
                "(mail=ana.anic@securecar.test)",
                (AttributesMapper<String>) attributes -> attributes.get("mail").get().toString()
        );

        List<String> employeeEmails = ldapTemplate.search(
                PEOPLE_SEARCH_BASE,
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
                GROUPS_SEARCH_BASE,
                "(&(objectClass=groupOfNames)(member=uid=marko.markovic,ou=people,dc=securecar,dc=test))",
                (AttributesMapper<String>) attributes -> attributes.get("cn").get().toString()
        );

        List<String> managerGroups = ldapTemplate.search(
                GROUPS_SEARCH_BASE,
                "(&(objectClass=groupOfNames)(member=uid=ana.anic,ou=people,dc=securecar,dc=test))",
                (AttributesMapper<String>) attributes -> attributes.get("cn").get().toString()
        );

        assertEquals(1, technicianGroups.size());
        assertEquals("SERVICE_TECHNICIANS", technicianGroups.get(0));
        assertEquals(1, managerGroups.size());
        assertEquals("SERVICE_MANAGERS", managerGroups.get(0));
    }

    @Test
    void technicianDirectoryReturnsOnlyMembersOfTechnicianGroup() {
        List<Technician> technicians = technicianDirectory.findAll();

        assertEquals(1, technicians.size());
        assertEquals("marko.markovic", technicians.get(0).getId());
        assertEquals("Marko Markovic", technicians.get(0).getDisplayName());
        assertEquals("marko.markovic@securecar.test", technicians.get(0).getEmail());
    }
}
