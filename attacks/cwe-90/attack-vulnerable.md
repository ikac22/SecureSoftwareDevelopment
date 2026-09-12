# CWE-90 — vulnerable application

## Goal

Demonstrate the LDAP injection described in issue #22 against the unmitigated technician-search implementation.

The experiment keeps all request parameters fixed except for the injected predicate and observes whether the response changes from non-empty to empty. This provides the blind true/false oracle described in the thesis without attempting password extraction or any destructive action.

## Entry point

`GET /services/1/available-slots`

Common request parameters:

- `date=2030-06-01`
- `estimatedDurationMinutes=60`

The two probes are:

```text
true:  *)(mail=m*)(cn=*
false: *)(mail=z*)(cn=*
```

The seeded LDAP directory contains technician e-mail addresses beginning with `m`, while none begin with `z`. The two probes therefore differ only in the injected `mail` predicate.

## Source-to-sink flow

```text
HTTP search parameter
        |
        v
ServiceController.availableSlots(...)
        |
        v
ServiceWorkflowService.findAvailableSlots(...)
        |
        v
normalizeTechnicianSearch(search)
        |
        v
LdapTechnicianDirectory.search(query)
        |
        v
"(&(objectClass=inetOrgPerson)(" + attribute + "=*" + query + "*))"
        |
        v
LdapTemplate.search(...)
        |
        v
LDAP filter parser / evaluator
        |
        v
onlyTechnicians(...)
```

The vulnerable boundary is the direct string concatenation in `LdapTechnicianDirectory.search`. LDAP filter metacharacters from the HTTP parameter become part of the filter grammar instead of remaining literal search data.

For the `cn` iteration, the true probe changes the effective structure so that an additional `(mail=m*)` predicate is evaluated. The false probe injects `(mail=z*)` instead. A reproducible difference between the two HTTP responses demonstrates that the client-controlled value changed LDAP filter semantics.

## Automated reproduction

The shared experiment test is:

```text
src/test/java/com/zuehlke/securesoftwaredevelopment/Cwe90ExperimentTests.java
```

The same test class is intended to run against both vulnerable and mitigated snapshots. For the vulnerable phase it is invoked with:

```bash
./mvnw -B \
  -Dtest=com.zuehlke.securesoftwaredevelopment.Cwe90ExperimentTests \
  -Dcwe90.phase=vulnerable \
  test
```

The test writes evidence under:

```text
attacks/cwe-90/evidence/vulnerable/
├── true-probe-request.txt
├── true-probe-response.json
├── false-probe-request.txt
├── false-probe-response.json
└── result.txt
```

## Vulnerable PASS criteria

The vulnerable run passes only when:

- both requests return HTTP 200;
- the true probe returns at least one technician availability result;
- the false probe returns no technician availability results;
- `predicateOracleObserved=true` is recorded in `result.txt`.

This is deliberately narrower than a full LDAP data-exfiltration scenario. The experiment proves syntax injection by showing that a client-controlled predicate changes the result of the real LDAP query.

## Expected mitigation boundary

The mitigation must be applied before interpolation into the LDAP filter. The user-controlled search value must be RFC 4515 encoded so characters such as `*`, `(`, `)`, `\\` and NUL remain data. The normal substring behavior is then preserved by the application-owned wildcard characters surrounding the encoded value.

After mitigation, the exact same two probes must no longer form different LDAP predicates. A legitimate search such as `Jovanovic` must continue to return `jelena.jovanovic` as a positive control.
