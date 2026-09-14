# How it works

An ADF application describes itself more completely than most frameworks do. Entity objects name
tables and columns and their types. View objects carry the query, its WHERE clause and its bind
variables. Application modules say which queries the application exposed together. Page
definitions say which screens read which query. The authorization policy says which role could
reach what.

All of that is XML sitting in the source tree. This reads it.

## What it reads

| file | what it gives |
|---|---|
| `*EO.xml` | tables, columns, types, keys, constraints, validators, associations |
| `*VO.xml` | the query: select list, from list, WHERE clause, bind variables, criteria |
| `*AM.xml` | which view instances were exposed together, and the links between them |
| `*.rpx`, `*Resources.xml` | published REST URLs and the grants on them |
| `*PageDef.xml` | which screen reads which query |
| `*.bcs` | Groovy expressions attached to attributes and validators |
| `jazn-data.xml` | roles and what they could reach |

It reads the hand-written Java too, but only to describe it. Knowing a custom class exists is
enough to warn about and not enough to price: an override that applies a view criteria and returns
a count is an afternoon, one that walks a row set issuing its own SQL is a week, and scoring both
the same makes every estimate wrong. So each method is read and classified — has a direct Spring
equivalent, needs review, or must be rewritten — and nothing is executed, rewritten or guessed at.

## What it writes

This half writes an assessment: a report, and the answers the commands print. The rest of this
page describes what **adfmig Pro** generates, because what the assessment is judging is whether
that generation would succeed.

A Maven project that depends on neither adfmig nor the ADF runtime. That independence is the
point of the migration: nothing generated here ties you to the thing you are leaving, or to the
tool that moved you.

```
src/main/java/<base>/
├── <resource>/     controller, service, repository, DTO, specifications — one directory each
├── entity/         shared: several resources read the same tables
├── shared/         paging types every resource uses
├── exception/
└── config/
```

Grouped by resource rather than by layer, because a team migrating off ADF is rarely migrating
to one application, and moving a resource into its own service should be moving one directory.

Alongside the code:

- **`README.md`** — how to run it and what to configure
- **`MIGRATION-CHECKLIST.md`** — everything left for a person, with what to do about each
- **`MODEL.md`** — the data model as a diagram
- **`SPLITTING.md`** — which resources could become separate services, and which shared table
  stands in the way of the ones that cannot

## What it guarantees

**The URL, the shape and the roles are the originals.** A caller written against the ADF
application still works: same paths, same `{items, count, hasMore}` envelope, same roles guarding
the same endpoints. That is what makes a migration comparable rather than a rewrite.

**It never guesses.** Where the metadata does not say what something means, the generated code
carries a `TODO` naming it and the checklist explains it. A gap that is obvious is better than a
plausible invention.

**Generated projects validate their own mapping.** They run with Hibernate's `ddl-auto: validate`,
so every column, type and key is checked against the real schema at startup. A mapping that does
not match refuses to boot rather than failing later against one row.

## What it cannot do

**Custom Java is not translated.** ADF applications carry `EntityImpl` and `ViewObjectImpl`
subclasses whose behaviour is in code, not metadata. Those are reported, not converted.

**Groovy is not interpreted.** Expressions are carried across as comments beside the attribute
they belonged to.

**The front end is not migrated.** ADF Faces is a server-side component framework with no
equivalent in Spring Boot. What the tool produces for a UI application is the data layer and an
API; the screens are a separate project.

**Nothing has been compared against a running ADF application.** Every check is against the
database — that the generated code compiles, starts, maps correctly and returns the rows the
query describes. Whether it behaves identically to the original under load, in a transaction, or
at an edge the metadata does not describe, is unverified. That is the honest limit, and it is
worth knowing before trusting the output with something that matters.
