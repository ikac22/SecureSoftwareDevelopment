package com.zuehlke.securesoftwaredevelopment;

import com.zuehlke.securesoftwaredevelopment.domain.Technician;
import com.zuehlke.securesoftwaredevelopment.service.TechnicianDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        List<String> ids = technicians.stream().map(Technician::getId).collect(Collectors.toList());

        assertTrue(technicians.size() >= 10);
        assertTrue(ids.contains("marko.markovic"));
        assertTrue(ids.contains("jelena.jovanovic"));
        assertFalse(ids.contains("ana.anic"));
        assertFalse(ids.contains("nikola.nikolic"));
    }

    @Test
    void technicianDirectorySearchesNameSurnameAndEmail() {
        assertEquals("marija.maric", technicianDirectory.search("Marija").get(0).getId());
        assertEquals("jelena.jovanovic", technicianDirectory.search("Jovanovic").get(0).getId());
        assertEquals("petar.petrovic", technicianDirectory.search("petar.petrovic@securecar.test").get(0).getId());
    }

    @Test
    void findByIdReturnsOnlyTechnicianGroupMembers() {
        Optional<Technician> technician = technicianDirectory.findById("marko.markovic");

        assertTrue(technician.isPresent());
        assertEquals("Marko Markovic", technician.get().getDisplayName());
        assertFalse(technicianDirectory.findById("ana.anic").isPresent());
        assertFalse(technicianDirectory.findById("nikola.nikolic").isPresent());
    }
}
