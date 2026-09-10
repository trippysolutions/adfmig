# adfmig

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

## Trying it

The tool is validated against public ADF sample applications rather than assumptions about the
format:

```
./scripts/setup.sh
adfmig apps ~/adf
```

one sample application publishes REST. another is a larger, more realistic sample — seventeen entities, nineteen
view links — and is the realistic one.

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

Sample applications and fetched schema scripts go to a working directory outside the project,
`~/adf` by default and `$ADF_DIR` if you would rather they went elsewhere. The corpus
alone is over 700 MB, and a directory of Oracle samples beside your source is clutter every time
you look at the project, whether or not git ignores it.

## Contributing

Issues are welcome, particularly ones with the ADF metadata that caused them. This tool exists
because real ADF applications turned out not to match what the documentation implies, and every
odd file makes it better: XML declaring an encoding it is not in, expert-mode SQL, `.bcs` Groovy,
namespaced connection definitions. If adfmig reads your application badly, the file that confused
it is the most useful thing you can send.

MIT licensed. See [LICENSE](LICENSE).
