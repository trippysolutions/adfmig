# adfmig

[![build](https://github.com/trippysolutions/adfmig/actions/workflows/build.yml/badge.svg)](https://github.com/trippysolutions/adfmig/actions/workflows/build.yml)
[![release](https://img.shields.io/github/v/release/trippysolutions/adfmig?label=release)](https://github.com/trippysolutions/adfmig/releases/latest)
[![licence](https://img.shields.io/badge/licence-MIT-blue)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-21%2B-orange)](https://adoptium.net)

**Trippy Solutions** · [trippysolutions.com](https://trippysolutions.com)

Tells you what it would take to move an Oracle ADF application to Spring Boot.

It reads the application's own metadata — entity objects, view objects, application modules, REST
resources, page definitions, Groovy expressions and the authorization policy — and reports what is
there: how large the migration really is, which applications share a database, what the published
REST surface resolves to in SQL, which endpoints are protected and by what, how much of the code
is never reached, and where database credentials sit in source.

Everything runs on your machine. There is no service, nothing is uploaded, and the tool makes no
network call of any kind. The same input produces the same output every run.

```
adfmig apps ~/adf                 what is in the estate, and in what order it can be migrated
adfmig report ~/adf/Payments      the assessment, as a self-contained HTML file
```

## Installing

Requires JDK 21 or later. Download `adfmig.jar` from
[releases](https://github.com/trippysolutions/adfmig/releases), or build it:

```
mvn package
java -jar adfmig-cli/target/adfmig.jar --help
```

A one-line launcher, so the examples here work as written:

```
alias adfmig='java -jar /path/to/adfmig.jar'
```

## Starting

```
adfmig
```

With no arguments and a terminal attached, adfmig walks through it: find your applications, then
assess one. It is the fastest way to see what the tool makes of your own code.

Everything it does is also a command, for when you know what you want.

```
adfmig apps    <estate>   Find the applications under a path: how each is consumed, what
                          database each reaches, which depend on one another, in what order
                          they can be migrated, and where credentials sit in source.

adfmig scan    <app>      Inventory one application.

adfmig analyze <app>      Resolve its published REST surface: URL to query to table, with
                          the security grant on each endpoint.

adfmig report  <app>      Assess it. Writes a self-contained HTML report: complexity,
                          effort, security findings, dead code.

adfmig glossary           What each ADF concept becomes in Spring, and why.
adfmig start              The walkthrough, explicitly.
```

Long steps show progress at a terminal and print plain lines when output is redirected, so a report
or a build log never receives escape codes. `NO_COLOR` and `ADFMIG_PLAIN` are respected.

## The first thing it tells you

Which of two things you have. It decides the size of the job more than anything else:

| | Publishes ADF BC REST | ADF Faces only |
|---|---|---|
| Backend | can be generated | can be generated |
| API | **the original contract, preserved** | a new API, derived from what the screens read |
| Provable against the original | yes, endpoint by endpoint | no — there is nothing to compare |
| What it is | a migration | a backend migration plus a front-end rebuild |

An application that already publishes REST can be replaced behind its existing callers. One that is
ADF Faces only cannot, because its API is its screens.

## Updates

adfmig never checks for them. It makes no network call of any kind — not for updates, not for
telemetry — because it is run inside networks that reach nothing, by people who need to be able to
say that no code left the building. An update check would be the one thing that broke that.

So new versions do not arrive on their own. To hear about them, **Watch → Custom → Releases** on
this repository, or read [CHANGELOG.md](CHANGELOG.md).

Updating is replacing one file. There is no installer, no daemon, nothing in your home directory,
and no state to migrate — download the new jar and delete the old one. An older jar keeps working
forever; nothing expires and nothing is revoked.

## Something read wrong?

[Open an issue](https://github.com/trippysolutions/adfmig/issues/new/choose). If adfmig
misread your application, the ADF file that confused it — with anything sensitive removed — is the
single most useful thing you can send. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Trying it

Point it at anything with ADF applications underneath — one application, a directory of them, or
a whole estate. It reads what it finds and reports on all of it:

```
adfmig apps ~/your-adf
adfmig report ~/your-adf/SomeApplication
```

Nothing is uploaded, and nothing is written outside the directory you name.

Before it goes near work that matters it is exercised against a corpus of several hundred real
ADF applications: each one read, converted, compiled and started against an Oracle database, with
the result checked against the rows that database holds. Composite keys, entity inheritance,
expert-mode SQL, polymorphic view rows and Oracle's older outer-join syntax are all in that
corpus deliberately, because those are the cases that break a migration.

## What this repository is

This is the assessment tool, and it is complete: no limits, no licence check, no account, nothing
withheld. Assess one application or three hundred.

Generating the Spring Boot project is a separate product, **adfmig Pro**, and it is sold. Its code
is not in this repository — so there is no check here to remove and nothing here to unlock. Rather
than ship a restriction anyone could delete in a minute and call it a tier, the free tool simply
does not contain the part that is paid for.

If the assessment says the migration is worth doing, [trippysolutions.com](https://trippysolutions.com).

## Layout

```
adfmig-core     the model everything works from
adfmig-parser   reads ADF metadata and custom Java into that model
adfmig-report   renders the assessment
adfmig-cli      the commands
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security issues go privately, per [SECURITY.md](SECURITY.md).

MIT licensed. See [LICENSE](LICENSE).
