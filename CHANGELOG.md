# Changelog

What changed, and whether you need to care. Versions follow [semver](https://semver.org): the
first number changes when output you might depend on changes shape, the second when something is
added, the third when something is fixed.

Nothing in adfmig checks for updates or contacts anything, so new versions do not arrive on their
own. To hear about them: **Watch → Custom → Releases** on this repository.

## 0.1.3

**Nothing in the free tool changed.** The jar behaves exactly as 0.1.2 did; the version moves
because the build is versioned as a whole. If you are using `adfmig` to assess an estate, there is
no reason to upgrade.

adfmig Pro gained a backlog export. The migration checklist it writes beside a generated project
is addressed to the developer who will do the work — every finding, in place, with the file to
open. That is the wrong shape for whoever has to decide who does it and how long it takes: one
real application produces 180 rows, and nobody plans against 180 rows. `--backlog jira|gitlab|trello`
now also writes a CSV those tools import, where one ticket is one finding in one file rather than
one occurrence, and tickets are grouped by the kind of work rather than by the code that found it.
On that application it turns 180 findings into 77 tickets. It writes a file rather than calling an
API, so it needs no token and creates nothing twice.

## 0.1.2

The console says the answer first. `adfmig apps` printed several hundred rows before the summary
anyone was reading it for; now it prints the summary, and the per-application table is behind
`--list`, with `--filter` and `--profile` to narrow it. `adfmig analyze` printed thirteen lines per
endpoint, so on an application of any size the security findings scrolled past unread; the surface
is now one row per endpoint with the detail behind `--detail`. Every command reports a verdict —
whether the source says enough to generate working code — before any arithmetic.

The walkthrough no longer loses people at its first two questions. A path it cannot find is
answered with the two mistakes people actually make: a typo in one segment, and an absolute path
where a path under the home directory was meant. The application list is a shortlist you can
search rather than several hundred numbered lines.

Every command's `--help` now carries examples, and there is a full
[command reference](docs/commands.md).

Added: [`docs/commands.md`](docs/commands.md). Three diagnostics documented that the generator
could already report — `ENTITIES_DISAGREE_ON_COLUMN`, `VIEW_INSTANCE_NOT_PUBLISHED` and
`VIEW_NEEDS_UNGENERATED_ENTITY` — and `ENTITY_EXTENDS_ANOTHER` narrowed: a genuine ADF subtype is
now carried across rather than reported.

## 0.1.1

**If you produced a report with 0.1.0, its estate total was too high.** A project containing no
entity, view object or application module — a schema or DDL project, a shell that exists to be
depended on — was still charged a flat five person-days for project scaffolding. On Oracle's own
sample application that was five of twenty-one days: a quarter of the total, for a project with nothing
in it. Such a project is now reported as having nothing to migrate, and contributes nothing to the
total. Re-run any assessment you have circulated.

Fixed, all found by generating an application and running it against a real Oracle database rather
than against the test suite:

- The generated API was missing two of the operations ADF published. ADF serves a single row and
  the collection under the same `get` grant, and an `update` grant as a PATCH on that row; only the
  collection was generated, so a caller written against the original found `GET /Employees/199` and
  `PATCH /Employees/199` answering 500. Nothing warned about it. *(adfmig Pro)*
- Updates wrote every column rather than the ones that changed, so Oracle fired `UPDATE OF <column>`
  triggers the original application never fired — editing a salary wrote a job-change history row.
  Generated entities are now `@DynamicUpdate`. *(adfmig Pro)*
- Every mistake a caller could make returned 500 with a stack trace: a missing row, a key of the
  wrong type, the wrong method, an unroutable path, a malformed body. They now answer 404, 400,
  405, 404 and 400, as ADF did. *(adfmig Pro)*
- Help in the free tool offered `generate` and `license`, which are not in it, and described an
  assessment limit that no longer exists.
- The HTML report says who produced it, and ends by naming what it cannot answer.

The acceptance check grew from 34 to 45, including a database reset so the ones that write to
Oracle mean the same thing on a second run.

## 0.1.0

First public release.

- Survey an estate: what applications are under a path, how each is consumed, which database each
  reaches, which depend on one another, and in what order they can be migrated
- Assess one application: complexity and effort per component, security findings, dead code, and
  where credential material sits in source
- Resolve a published ADF BC REST surface end to end — URL to view object to SQL to table — with
  the authorization grant on each endpoint
- Self-contained HTML report, and a JSON export of the same assessment
- Reads what real applications actually contain rather than what the format documents: XML
  declaring an encoding it is not in, expert-mode SQL in CDATA, `.bcs` Groovy, namespaced
  connection definitions, and application modules whose configuration is not where it should be

Known limits, stated plainly: effort estimates rank cost reliably and predict it loosely, because
nothing has yet been calibrated against a migration that finished. Components inside ADF Library
JARs are not readable and are reported as unknown rather than as zero.
