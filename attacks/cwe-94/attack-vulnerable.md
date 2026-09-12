# CWE-94 — vulnerable application

## Goal

Demonstrate the SpEL code-injection path described in issue #23 in the loyalty discount preview.

The experiment intentionally uses the basic, non-destructive logic-injection payload from the thesis. It does not require command execution or a reverse shell: the observable proof is that changing only `partnerCode` changes the program logic evaluated by SpEL and therefore changes the returned price.

## Entry point

`POST /loyalty-calculator`

The two requests keep all business parameters identical:

```text
tier=BRONZE
laborPrice=8000
partsPrice=2000
estimatedDurationMinutes=60
completedServices=1
```

Only `partnerCode` differs:

```text
baseline: NONE
attack:   x' == 'x' or 'x
```

For the vulnerable implementation, the expected deterministic result is:

```text
baseline discountedPrice = 10000.00
attack   discountedPrice = 9500.00
```

## Source-to-sink flow

```text
HTTP partnerCode
      |
      v
LoyaltyCalculatorController.calculate(...)
      |
      v
ServicePricingPolicyEvaluator.evaluatePreview(...)
      |
      v
renderExpression(...)
      |
      +--> template.replace("${partnerCode}", "'" + partnerCode + "'")
      |
      v
SpelExpressionParser.parseExpression(expressionText)
      |
      v
Expression.getValue()
```

The vulnerable boundary is the textual interpolation performed before parsing. A quote in `partnerCode` can terminate the intended string literal, after which the remaining request-controlled text is interpreted as SpEL syntax.

The payload `x' == 'x' or 'x` turns the partner-code comparison into a true boolean condition. The BRONZE policy therefore takes the 5% discount branch even though the supplied text is not a legitimate partner code.

## Automated reproduction

`Cwe94ExperimentTests` is the single experiment harness used for both application states. It sends the baseline request and the attack request through the real `LoyaltyCalculatorController` endpoint using `MockMvc` and writes the observed evidence under:

```text
attacks/cwe-94/evidence/<phase>/
├── baseline-request.txt
├── baseline-response.json
├── attack-request.txt
├── attack-response.json
└── result.txt
```

The same test is executed with one of two phase values:

```text
-Dcwe94.phase=vulnerable
-Dcwe94.phase=mitigated
```

The final runner resolves immutable Git snapshots instead of trusting the currently checked-out branch, so the same test and payload can be repeated against both the vulnerable and mitigated source trees.

## Vulnerable PASS criteria

The vulnerable snapshot passes only when all of the following hold:

- baseline and attack both return HTTP 200;
- baseline `discountedPrice` is `10000.00`;
- changing only `partnerCode` to `x' == 'x' or 'x` changes the returned price;
- attack `discountedPrice` is `9500.00`;
- the exact requests, responses and result summary are written to `evidence/vulnerable/`.

## Snapshot model

The last commit that contains this harness while production code still performs string interpolation is the intended target of immutable tag `cwe-94-vulnerable`.

After mitigation, the final branch state is the intended target of immutable tag `cwe-94-mitigated`. `attacks/cwe-94/tag-snapshots.sh` and `attacks/cwe-94/run-attack.sh` manage and compare those two states in the same way as the CWE-943 experiment in PR #25.
