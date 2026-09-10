# Changelog

What changed, and whether you need to care. Versions follow [semver](https://semver.org): the
first number changes when output you might depend on changes shape, the second when something is
added, the third when something is fixed.

Nothing in adfmig checks for updates or contacts anything, so new versions do not arrive on their
own. To hear about them: **Watch → Custom → Releases** on this repository.

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
