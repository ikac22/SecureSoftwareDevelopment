package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.Technician;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Component;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LdapTechnicianDirectory implements TechnicianDirectory {
    private static final String PEOPLE_SEARCH_BASE = "ou=people,dc=securecar,dc=test";
    private static final String GROUPS_SEARCH_BASE = "ou=groups,dc=securecar,dc=test";
    private static final String TECHNICIAN_GROUP_FILTER =
            "(&(objectClass=groupOfNames)(cn=SERVICE_TECHNICIANS))";

    private final LdapTemplate ldapTemplate;

    public LdapTechnicianDirectory(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }

    @Override
    public List<Technician> findAll() {
        Set<String> technicianUids = findTechnicianUids();
        if (technicianUids.isEmpty()) {
            return Collections.emptyList();
        }

        return ldapTemplate.search(
                PEOPLE_SEARCH_BASE,
                "(objectClass=inetOrgPerson)",
                (AttributesMapper<Technician>) attributes -> new Technician(
                        requiredAttribute(attributes.get("uid"), "uid"),
                        requiredAttribute(attributes.get("cn"), "cn"),
                        requiredAttribute(attributes.get("mail"), "mail")
                )
        ).stream()
                .filter(technician -> technicianUids.contains(technician.getId()))
                .sorted((left, right) -> left.getDisplayName().compareTo(right.getDisplayName()))
                .collect(Collectors.toList());
    }

    private Set<String> findTechnicianUids() {
        List<List<String>> groupMembers = ldapTemplate.search(
                GROUPS_SEARCH_BASE,
                TECHNICIAN_GROUP_FILTER,
                (AttributesMapper<List<String>>) attributes -> memberDns(attributes.get("member"))
        );

        Set<String> technicianUids = new HashSet<>();
        for (List<String> members : groupMembers) {
            for (String memberDn : members) {
                technicianUids.add(uidFromDn(memberDn));
            }
        }
        return technicianUids;
    }

    private List<String> memberDns(Attribute memberAttribute) throws NamingException {
        if (memberAttribute == null) {
            return Collections.emptyList();
        }

        List<String> members = new ArrayList<>();
        NamingEnumeration<?> values = memberAttribute.getAll();
        try {
            while (values.hasMore()) {
                members.add(values.next().toString());
            }
        } finally {
            values.close();
        }
        return members;
    }

    private String uidFromDn(String distinguishedName) {
        try {
            for (Rdn rdn : new LdapName(distinguishedName).getRdns()) {
                if ("uid".equalsIgnoreCase(rdn.getType())) {
                    return rdn.getValue().toString();
                }
            }
        } catch (NamingException exception) {
            throw new IllegalStateException("Invalid technician member DN", exception);
        }
        throw new IllegalStateException("Technician member DN does not contain uid");
    }

    private String requiredAttribute(Attribute attribute, String name) throws NamingException {
        if (attribute == null || attribute.size() == 0) {
            throw new IllegalStateException("LDAP technician is missing " + name);
        }
        return attribute.get().toString();
    }
}
