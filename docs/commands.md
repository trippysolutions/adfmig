# Command reference

Every command, every option, and what each is for.

Nothing here contacts a network, writes outside the directory you name, or keeps state between
runs. The same input produces the same output.

```
alias adfmig='java -jar /path/to/adfmig.jar'
```

---

## `adfmig` — the walkthrough

```
adfmig
adfmig start
```

With a terminal attached and no arguments, adfmig walks through finding your applications and
assessing one. It is the fastest way to see what the tool makes of your own code, and it is the
same work the commands below do.

At the first prompt, a path starting with `~/` is read from your home directory and one starting
with `/` from the root of the disk. A path it cannot find, it tries to suggest.

Redirected or piped, it refuses rather than waiting for an answer nobody is there to give.

---

## `adfmig apps` — what is in an estate

```
adfmig apps <path>
```

Finds every ADF application under a path and reports how each is consumed, what database each
reaches, which depend on one another, in what order they can be migrated, and where credentials
sit in source.

| option | |
|---|---|
| `-l`, `--list` | List every application. Without it only the summary is printed, because an estate of any size is hundreds of rows nobody reads |
| `-f`, `--filter TEXT` | List only applications whose name or path contains this, case insensitively. Implies `--list` |
| `--profile KIND` | List only one kind: `rest`, `ui`, `model` or `empty`. Implies `--list` |
| `-o`, `--json FILE` | Write the whole survey as JSON, for another tool to read |

```
adfmig apps ~/adf                          how many, of what kind, and what they share
adfmig apps ~/adf --list                   every one of them, as a table
adfmig apps ~/adf --filter payments        only the ones that match
adfmig apps ~/adf --profile rest           only those already publishing REST
adfmig apps ~/adf --json estate.json       the whole survey, as data
```

The summary is the point: how many applications already publish REST (those can be migrated behind
their existing callers), how many are ADF Faces (those need the front end rebuilt), how many are
shared libraries, and how many have no business model at all.

It also reports **schemas written by more than one application**, which is the most expensive
thing to discover late: migrating one of them while its neighbour stays on ADF leaves two writers
on the same rows using different optimistic locking, and updates are lost silently.

---

## `adfmig scan` — what one application is made of

```
adfmig scan <path>...
```

The inventory: every artifact, grouped by layer, with a verdict on how the application is
consumed. Takes more than one path.

| option | |
|---|---|
| `--show-ignored` | Include artifact types irrelevant to a migration |
| `-o`, `--json FILE` | Write the full scan result as JSON |

```
adfmig scan ~/adf/Payments
adfmig scan ~/adf/Payments --show-ignored
```

---

## `adfmig analyze` — what each URL actually does

```
adfmig analyze <path>...
```

Resolves the published REST surface down to the database: URL to view object to table, with the
security grant on each endpoint, then the security findings and the migration notes.

| option | |
|---|---|
| `-d`, `--detail` | Expand every endpoint: bind variables, criteria, operations, grants and custom Java. Without it the surface is one row per endpoint |
| `--sql` | Show the full query behind each endpoint. Implies `--detail` |
| `--all` | Analyse every application under the path, each on its own |
| `-o`, `--json FILE` | Write the resolved model as JSON |

```
adfmig analyze ~/adf/Payments              every endpoint, one row each
adfmig analyze ~/adf/Payments --detail     bind variables, criteria, grants
adfmig analyze ~/adf/Payments --sql        the query behind each endpoint
adfmig analyze ~/adf --all                 every application under the path
```

Two sections are worth reading even when the rest is skimmed. **SECURITY** reports endpoints
carrying no grant — unreachable in ADF, so a migration must deny them rather than publish them
open — and permissions granted to named users rather than roles. **MIGRATION NOTES** reports
hand-written Java that metadata cannot describe, ADF runtime types, expert-mode SQL, dead
components and Groovy that needs translating.

---

## `adfmig report` — what it would take

```
adfmig report <path>...
```

The assessment. Prints a verdict and the arithmetic behind it, and writes a self-contained HTML
report you can send to somebody who will never run this tool.

| option | |
|---|---|
| `--all` | Assess every application under the path, each on its own, with an estate total |
| `-o`, `--out DIR` | Where to write the reports (default `./reports`) |
| `--json` | Also write each assessment as JSON beside its report |

```
adfmig report ~/adf/Payments               assess one application
adfmig report ~/adf --all                  assess an estate, with a total
adfmig report ~/adf/Payments -o out        write the HTML somewhere else
```

The verdict is about whether the source says enough to generate working code, which is a different
question from how much work the migration is:

| | |
|---|---|
| **READY TO MIGRATE** | every component carries what a generator needs |
| **MIGRATES WITH REVIEW** | some components need a person to decide something first |
| **NOT READY** | too much is missing to migrate as it stands |
| **NOTHING TO MIGRATE** | no business model here |

Effort weights are uncalibrated until measured against a completed migration, and the report says
so and shows the formula.

---

## `adfmig glossary` — what each ADF term becomes

```
adfmig glossary
```

Every ADF concept, what it is, and what it becomes in Spring. For people who have to read an
assessment without having worked in ADF.

---

## Everywhere

| | |
|---|---|
| `-h`, `--help` | The command's own help, with examples |
| `-V`, `--version` | Which build this is |
| `NO_COLOR` | Set it and output carries no escape codes ([no-color.org](https://no-color.org)) |
| `ADFMIG_PLAIN` | The same, for environments where `NO_COLOR` means something else |

Progress is shown at a terminal and suppressed when output is redirected, so a report or a build
log never receives escape codes.

Exit codes: `0` success, `2` a usage or input error.

---

## Not in this tool

Generating the Spring Boot project, comparing a generated schema against a real database, and
calling every endpoint of a running replacement are **adfmig Pro** —
[trippysolutions.com](https://trippysolutions.com). Its code is not in this repository, so there
is no check here to remove and nothing here to unlock.
