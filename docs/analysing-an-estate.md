# Analysing an estate

The first question about an ADF migration is how big it is, and the honest answer is usually
that nobody knows. Applications were written over a decade by people who have moved on, some of
them share a database, some are barely used, and the only record of what they do is the
applications themselves.

This is how to find out, in the order the answers are useful.

## 1. What is out there

Point it at a directory with applications anywhere underneath. It reads them all.

```
adfmig apps ~/adf
```

```
272 application(s) under /Users/you/adf

  BY PROFILE

      ┌──────────────┬──────────────────────────────────────────────┐
      │ APPLICATIONS │ HOW IT IS CONSUMED                           │
      ├──────────────┼──────────────────────────────────────────────┤
      │            2 │ already REST — contract-preserving migration │
      │          206 │ ADF Faces UI — front end needs rewriting     │
      │           49 │ model only — likely a shared library         │
      │           15 │ no business model                            │
      └──────────────┴──────────────────────────────────────────────┘
```

That is the answer most of the time, so it is what you get. The applications themselves are a
table behind `--list`, because an estate of any size is hundreds of rows nobody reads:

```
adfmig apps ~/adf --list
adfmig apps ~/adf --filter payments        only the ones that match
adfmig apps ~/adf --profile rest           only those already publishing REST
```

```
┌─────────────────┬────┬────┬────┬──────┬──────────────────────────────────────────────┐
│ APPLICATION     │ EO │ VO │ AM │ REST │ PROFILE                                      │
├─────────────────┼────┼────┼────┼──────┼──────────────────────────────────────────────┤
│ Payments        │ 41 │ 58 │  3 │   12 │ already REST — contract-preserving migration │
│ Reporting       │ 17 │ 22 │  1 │    0 │ ADF Faces UI — front end needs rewriting     │
│ SharedLibrary   │  6 │  6 │  1 │    0 │ model only — likely a shared library         │
└─────────────────┴────┴────┴────┴──────┴──────────────────────────────────────────────┘
```

**EO** is entity objects — roughly tables. **VO** is view objects — roughly queries. **AM** is
application modules, which is how the original developers grouped things. **REST** is how many
URLs the application already publishes.

The profile is the useful column. An application publishing REST has callers who will notice a
difference, so it migrates first and is compared against carefully. One that is a UI has no
contract to preserve, and its front end is a separate piece of work. One with a model and no
screens is usually a library the others share, and migrating it alone achieves nothing.

Add `--json estate.json` to get the same thing as data.

## 2. Which applications share a database

This is the question that decides the order, and it is in the same output. Applications reading
the same schema cannot be migrated independently without deciding who owns what — the same
problem the generated `SPLITTING.md` describes within one application, a level up.

## 3. What one application actually is

```
adfmig report ~/adf/Payments
```

Writes a self-contained HTML file — no network, no assets to host — covering what the
application contains, what the migration involves, and what the tool could not work out on its
own. Open it in a browser; send it to whoever is asking for an estimate.

For the same thing as data:

```
adfmig report ~/adf/Payments --json
```

## 4. What its URLs really do

```
adfmig analyze ~/adf/Payments
```

Resolves each published URL to the query behind it and the tables that query reads, with the
role that guards it. This is the part nobody can reconstruct from documentation, and it is what
tells you whether an endpoint that looks harmless is reading six tables and a view nobody
remembers.

## 5. What is in a single application

```
adfmig scan ~/adf/Payments
```

An inventory: how many of each kind of artifact, how much custom Java, how much of it is
unreachable.

## Reading the estimate honestly

The assessment says what the metadata supports and marks what it cannot see. Hand-written Java is
read and classified — a direct Spring equivalent, needs review, or must be rewritten — but never
interpreted or converted, because an override that applies a criteria and returns a count is an
afternoon and one that walks a row set issuing its own SQL is a week, and scoring both the same
makes every estimate wrong. Groovy expressions are counted and quoted, not interpreted. Where an
estimate depends on something invisible, it says so rather than guessing, because an estimate that
hides its assumptions is worse than no estimate.

On a real estate the hand-written half is usually the larger one. It is worth reading that number
before quoting anything: the tool generates the part metadata describes, and a person writes the
rest.

## What it never does

It makes no network call, writes nothing outside the directory you name, and produces the same
output for the same input every run. It reads files; it does not run the application, connect to
its database, or need one.
