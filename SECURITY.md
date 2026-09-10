# Security

adfmig reads source code, sometimes in places where the source is the sensitive thing.

Two guarantees it makes, both testable:

- **It makes no network call.** Not for updates, not for telemetry, not for anything. Run it with
  the machine offline and everything works identically.
- **It does not read credential values.** Where credential material is found in source, the report
  gives its location and not its content. Passwords in `bc4j.xcfg` and wallet files are located,
  never opened.

If you find either of those to be untrue, that is a security issue and I want to know immediately.

## Reporting

Privately, through [trippysolutions.com](https://trippysolutions.com) — not as a public issue.

Include what you ran and what happened. If it involves metadata you cannot share, say so; a
description of its shape is usually enough to reproduce.

## Supported versions

The most recent release. This is a young project; fixes go forward rather than being backported.
