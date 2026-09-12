# Contributing

The most valuable thing you can send is **an ADF file that adfmig read wrong**.

This tool is exercised against several hundred applications, and nearly every improvement in
it came from one of them doing something none of the others did — a document declaring `Cp1252` and
not being in it, SQL hidden in a CDATA section, a connection definition behind a namespace, a
`.bcs` Groovy file where XML was expected. If it reads your application badly, the file that
confused it is worth more than a description of the symptom.

**Never post anything confidential.** No credentials, no customer data, no proprietary logic. The
declared encoding, the root element and the attribute names are usually all that matters, and a
file can be reduced to those before you attach it. If it cannot be shared at all, describe its
shape — that is often enough to reproduce.

## Building

```
mvn package
java -jar adfmig-cli/target/adfmig.jar --help
```

JDK 21 or later. CI builds on 21 and 25.

## Changes

Small, focused pull requests are easiest to take. A parser change should come with the metadata
that motivated it as a test fixture, reduced to the smallest file that still reproduces the
problem.

Two things this repository does not accept, so nobody wastes an afternoon:

- **Code generation.** Generating the Spring Boot project is a separate commercial product and is
  not part of this repository. A pull request adding a generator here cannot be merged.
- **Network calls.** Nothing in this tool contacts anything, including for update checks or
  telemetry. It is run inside networks that reach nothing, by people who need to be able to say
  that no code left the building. That is a hard rule, not a default.
