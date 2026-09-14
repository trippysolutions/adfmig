# Command reference

Every command, every option, and what each is for.

Nothing here contacts a network, writes outside the directory you name, or keeps state between
runs. The same input produces the same output.

```
alias adfmig='java -jar /path/to/adfmig.jar'
```

## Which command do I want?

| you want to know | command | tier |
|---|---|---|
| What is in this folder of applications? | `adfmig apps` | free |
| What is one application made of? | `adfmig scan` | free |
| What does each URL actually do, and who may call it? | `adfmig analyze` | free |
| What would migrating it take, in a document I can send? | `adfmig report` | free |
| What does this ADF word mean in Spring? | `adfmig glossary` | free |
| Just show me — I don't want to learn the flags | `adfmig` | free |
| Give me the Spring Boot project | `adfmig generate` | Pro |
| Does the database agree with what it expects? | `adfmig schema` | Pro |
| Does the running result actually answer? | `adfmig verify` | Pro |

The usual order is `apps` to find them, `report` to pick one, `generate` to build it, then
`schema` and `verify` to prove it.

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

## adfmig Pro

The commands below need a licence, and their code is not in this repository — there is no check
here to remove and nothing here to unlock. They are documented so you can see exactly what the
paid half does before talking to anyone: [trippysolutions.com](https://trippysolutions.com).

Everything above stays true of these: nothing is uploaded, no model is consulted, and `schema`
does not connect to your database.

### `adfmig generate` — the Spring Boot project

```
adfmig generate <path>... -o <dir>
```

Writes an ordinary Spring Boot 3 / Java 21 project: entities, repositories, services, controllers,
DTOs, security configuration, the build file and a README. It depends on neither adfmig nor the
ADF runtime, and it is yours to edit and own outright.

The original URLs are kept, so systems already calling the ADF application do not have to change.

| option | |
|---|---|
| `-o`, `--out DIR` | Where to generate (default `./generated`). Each application gets its own subdirectory |
| `-p`, `--package NAME` | Base package for the generated code. Derived from the ADF packages if omitted |
| `--layout SHAPE` | `feature` puts one resource's controller, service, repository and DTO together; `layered` puts every controller in one package, every service in another (default `feature`) |
| `--all` | Generate every application under the path, each into its own directory |
| `--force` | Overwrite files changed since they were generated. Without it, your edits are kept and reported |
| `--backlog TRACKER` | Also write `backlog.csv` for `jira`, `gitlab` or `trello` |
| `--spring-boot-version V` | Spring Boot version to declare in the build (default `3.3.5`) |
| `--json FILE` | Write every diagnostic as JSON, for a pipeline to read |
| `--fail-on SEVERITY` | Exit non-zero at `info`, `warning`, `skipped` or `error`. Without it only a generator failure is non-zero, because work left for a person is the expected outcome of a migration |
| `--license FILE` | Use the licence at this path for this run |

```
adfmig generate ~/adf/Payments -o out                    the project
adfmig generate ~/adf/Payments -o out --layout layered   controllers together, services together
adfmig generate ~/adf/Payments -o out --backlog jira     plus a backlog to import
adfmig generate ~/adf --all -o out                       the whole estate
adfmig generate ~/adf/Payments -o out --fail-on error    for a pipeline
```

Three files are written beside the project:

| | |
|---|---|
| `README.md` | What the application needs to start, its endpoints, and the authorities they require |
| `MIGRATION-CHECKLIST.md` | Everything left for a person, each with what to do about it |
| `schema.sql` | The Oracle schema the project expects, so it can be started before anyone grants access to a real database |

Anywhere the generator could not finish, the generated source carries a marker:

```
grep -rn "TODO(adfmig:" src/
```

### `adfmig generate --backlog` — work your team can plan

`MIGRATION-CHECKLIST.md` is written for the developer holding the keyboard: every finding, in
place, with the file to open. That is the wrong shape for whoever decides who does the work. A real
application produces around 180 rows, and nobody assigns 180 things.

`--backlog` writes `backlog.csv` beside it, where **one ticket is one finding in one file** rather
than one occurrence — sixty-nine endpoints with no grant all come from one `jazn-data.xml` and are
one decision, so they are one ticket with sixty-nine lines in it. Tickets are grouped into epics by
the kind of work rather than by the diagnostic code:

| epic | |
|---|---|
| Configuration and setup | the application will not start until these are answered |
| Data model gaps | entities and columns; everything else sits on top of them |
| Behaviour changes to confirm | it runs, but does not do what the original did |
| API surface not carried over | endpoints the original published and this does not |
| Security decisions | who may call what |
| Business logic to rewrite | stubs left beside the ADF original |
| Code to review | carried across as written, and worth a domain expert's eye |

They come out in that order, which is what blocks the work earliest rather than what is largest.

```
adfmig generate ~/adf/Payments -o out --backlog jira     Jira: import, then map the columns
adfmig generate ~/adf/Payments -o out --backlog gitlab   GitLab: title and description only
adfmig generate ~/adf/Payments -o out --backlog trello   Trello: create the lists first
```

Nothing calls anyone's API. It writes a file their importers read, so there is no token to issue
and nothing is created twice if you run it again.

### `adfmig schema` — does the database agree?

```
adfmig schema <path> --against <file>
```

Compares the schema the application expects against the one a database actually has. **Nothing
connects to a database**: `--against` takes the database's own DDL — a schema export, or anything
holding its `CREATE TABLE` statements — which is the one thing a customer can send before an
account has been agreed.

| option | |
|---|---|
| `--against FILE` | The database's own DDL. Required |
| `--show-unused` | Also list tables the database has that this application never reads |
| `--fail-on-difference` | Exit non-zero when anything differs, for a pipeline to act on |

```
adfmig schema ~/adf/Payments --against prod-schema.sql
adfmig schema ~/adf/Payments --against prod-schema.sql --show-unused
```

This is the only check that can find an error in the ADF metadata itself. Everything else is
derived from that metadata, so a mistake in it agrees with itself all the way down.

### `adfmig verify` — does the running result answer?

```
adfmig verify --url <url>
```

Calls every endpoint of a running generated application and reports what answered, what was denied,
and what failed. Compiling proves nothing about whether an application serves data.

| option | |
|---|---|
| `--url URL` | Where the application is running, e.g. `http://localhost:8080`. Required |
| `--user NAME` | Username for basic authentication |
| `--password PASS` | Password for basic authentication |
| `--show-serving` | List the endpoints that answered, not only the failures |
| `--timeout SECONDS` | How long to wait for one endpoint (default 20) |

```
adfmig verify --url http://localhost:8080
adfmig verify --url http://localhost:8080 --user tester --password tester --show-serving
```

### Contract tests

Generated projects include a test per endpoint that sends the same request to the ADF application
and to the replacement and fails on any difference, naming the JSON path where they diverge. Point
both at the same database snapshot:

```
mvn test -Dadfmig.legacy=http://old-host:7101/YourApp \
         -Dadfmig.migrated=http://localhost:8080
```

Without those properties they skip, so an ordinary build stays green with no ADF server running.

---

## What it does not do

**It migrates the backend, not the front end.** ADF Faces is a server-side component framework with
no equivalent in Spring Boot. Screens, task flows and page layouts are not converted, and most ADF
applications are mostly screens. `adfmig apps` tells you which of yours are which before you commit
to anything.
