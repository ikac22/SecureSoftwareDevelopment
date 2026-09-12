# CWE-90 — mitigated application

## Mitigation

The vulnerable search previously concatenated the trimmed HTTP `search` value directly into an LDAP filter. The mitigated implementation applies `LdapEncoder.filterEncode(normalizedQuery)` before interpolation.

The application still owns the surrounding wildcard characters used for substring search:

```java
String normalizedQuery = query.trim();
String escapedQuery = LdapEncoder.filterEncode(normalizedQuery);
String filter = "(&(objectClass=inetOrgPerson)(" + attribute + "=*" + escapedQuery + "*))";
```

This preserves the intended partial-match behavior while ensuring LDAP metacharacters from the request remain literal search data rather than filter syntax.

## Repeated attack

The mitigated experiment uses the exact same endpoint, service, date, duration and probe strings as the vulnerable run:

```text
true:  *)(mail=m*)(cn=*
false: *)(mail=z*)(cn=*
```

No payload rewriting is allowed between phases. The purpose of the comparison is to verify that only the application implementation changed.

After RFC 4515 encoding, both values are searched as literal text. The injected `(mail=...)` fragments therefore no longer become independent LDAP predicates and must no longer produce the vulnerable non-empty/empty oracle.

## Positive control

The experiment also sends the legitimate substring search:

```text
Jovanovic
```

The expected result is exactly one availability entry for technician `jelena.jovanovic`. This verifies that the mitigation does not disable the intended search functionality.

## Automated reproduction

From the final experiment branch, create the immutable snapshot tags and run both phases:

```bash
bash attacks/cwe-90/tag-snapshots.sh --push
bash attacks/cwe-90/run-attack.sh
```

The runner executes `Cwe90ExperimentTests` against both tagged source trees and stores the evidence separately:

```text
attacks/cwe-90/evidence/
├── vulnerable/
│   ├── true-probe-request.txt
│   ├── true-probe-response.json
│   ├── false-probe-request.txt
│   ├── false-probe-response.json
│   ├── result.txt
│   └── maven-output.txt
└── mitigated/
    ├── true-probe-request.txt
    ├── true-probe-response.json
    ├── false-probe-request.txt
    ├── false-probe-response.json
    ├── result.txt
    └── maven-output.txt
```

## Mitigated PASS criteria

The mitigated phase passes only when:

- both attack probes return HTTP 200;
- `predicateOracleObserved=false`;
- the true and false probe return the same number of results, demonstrating that the injected predicate no longer changes filter structure;
- the legitimate `Jovanovic` search still returns `jelena.jovanovic`;
- `positiveControlPassed=true` is recorded in the experiment result.

The experiment does not attempt password extraction. It stays within the basic, non-destructive LDAP injection scenario described in issue #22 and the thesis.
