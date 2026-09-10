# Changelog

What changed, and whether you need to care. Versions follow [semver](https://semver.org): the
first number changes when output you might depend on changes shape, the second when something is
added, the third when something is fixed.

Nothing in adfmig checks for updates or contacts anything, so new versions do not arrive on their
own. To hear about them: **Watch → Custom → Releases** on this repository.

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
