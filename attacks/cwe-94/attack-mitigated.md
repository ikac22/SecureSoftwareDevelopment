# CWE-94 — mitigated application

## Mitigation

The mitigation removes the syntax boundary that allowed request data to become executable SpEL text.

The pricing resource is now parsed as trusted policy text without replacing request values into that text. Runtime values are supplied as variables in a restricted `SimpleEvaluationContext`:

```text
trusted .spel policy
        |
        v
SpelExpressionParser.parseExpression(...)
        |
        v
SimpleEvaluationContext
  #basePrice
  #laborPrice
  #partsPrice
  #estimatedDurationMinutes
  #completedServices
  #partnerCode
        |
        v
Expression.getValue(context)
```

The pricing resources reference `#partnerCode` and the other bound values directly. Consequently, quote characters or SpEL-looking fragments inside `partnerCode` remain string data and cannot modify the parsed expression tree.

The policy files also no longer need `T(java.lang.Math).min(...)`. Their equivalent minimum selection is expressed with ordinary trusted SpEL operators, which allows the policies to execute inside `SimpleEvaluationContext` without exposing type references or general Java method invocation.

## Repeated attack

The experiment deliberately repeats the same values used in the vulnerable run:

```text
tier=BRONZE
laborPrice=8000
partsPrice=2000
estimatedDurationMinutes=60
completedServices=1
baseline partnerCode=NONE
attack partnerCode=x' == 'x' or 'x
```

The vulnerable snapshot returns `9500.00` for the attack. On the mitigated snapshot the same text is only the value of `#partnerCode`, so it does not satisfy either legitimate BRONZE partner-code condition and the result remains `10000.00`.

## Positive control

The same harness also sends the legitimate partner code `FLEET-10`. The mitigated BRONZE policy must still return `9500.00`, proving that the intended partner discount remains functional after the syntax/data separation.

## Automated comparison

Create the immutable snapshot tags once the branch is finalized:

```bash
bash attacks/cwe-94/tag-snapshots.sh --push
```

Then run the same experiment against both snapshots:

```bash
bash attacks/cwe-94/run-attack.sh
```

The runner checks out each tag in a temporary detached worktree and executes the same `Cwe94ExperimentTests` class with phase-specific expectations. The caller's checkout is not modified.

Evidence is written separately under:

```text
attacks/cwe-94/evidence/
├── vulnerable/
└── mitigated/
```

Each phase records the baseline request/response, attack request/response, the boolean result summary and Maven output captured by the runner.

## Mitigated PASS criteria

The mitigated snapshot passes only when:

- baseline and attack both return HTTP 200;
- baseline `discountedPrice` remains `10000.00`;
- the exact attack text no longer changes the price and also returns `10000.00`;
- the legitimate `FLEET-10` positive control still returns `9500.00`;
- the same experiment harness is used for both snapshots.

The important comparison is therefore not whether the malicious-looking text is rejected, but whether it has lost its ability to become SpEL syntax.
