# Diagnostic reference

Every diagnostic the generator can report, what it means, and what it carries.

`--fail-on <severity>` stops a build when anything reaches the severity you name: `info`,
`warning`, `skipped` or `error`. `--json <file>` writes them all as data, with the source
artifact, the subject, the generated file and the suggested action for each.

| Code | Severity | Means |
|---|---|---|
| `WRITE_FAILED` | error | Could not write the generated file |
| `ATTRIBUTE_HAS_NO_COLUMN` | skipped | An attribute carries no column, so there is nothing to map it to |
| `COMPOSITE_KEY_NOT_ADDRESSABLE` | skipped | The key has several columns, so one path variable cannot address a row |
| `ENDPOINT_NOT_SERVED` | skipped | The application module behind this URL generated no method for the view instance it reads, so there is nothing for a controller to call |
| `ENDPOINT_UNRESOLVED` | skipped | The chain from URL to query is broken in the source application |
| `ENTITY_EXTENDS_ANOTHER` | skipped | The entity extends another |
| `FILE_EDITED_NOT_OVERWRITTEN` | skipped | The file has changed since the generator wrote it, so it was left alone |
| `MASTER_DETAIL_NOT_EXPOSED` | skipped | The application exposes a nested collection through a view link, and the generated API does not |
| `NO_PRIMARY_KEY` | skipped | JPA has no identity to map |
| `OPERATION_NAME_ALREADY_USED` | skipped | Two view objects in one application module publish an operation with the same name and the same parameter types |
| `PROGRAMMATIC_VIEW_OBJECT` | skipped | The view object is populated by Java code, not by a query |
| `QUERY_FILTER_NOT_CARRIED_OVER` | skipped | The view object narrowed its query and the narrowing was not carried over, so the endpoint serves rows the original did not |
| `ASSOCIATION_UNRESOLVED` | warning | The association behind a relationship is missing or incomplete, so no mapping was written |
| `CONTRACT_TEST_NEEDS_A_ROW` | warning | A write comparison was generated but has no row to post |
| `CUSTOM_JAVA_HAS_EQUIVALENT` | warning | A custom method whose shape has a direct equivalent — applying a view criteria and counting rows, reading a sequence |
| `CUSTOM_JAVA_NEEDS_REVIEW` | warning | A custom method using ADF calls that map onto Spring, but which need reading first |
| `CUSTOM_JAVA_NEEDS_REWRITE` | warning | A custom method reaching into ADF's own transaction or row-set machinery, or overriding a hook ADF calls inside its write path |
| `CUSTOM_JAVA_NOT_TRANSLATED` | warning | A custom ADF Java class exists but its source was not found, so it could not be described |
| `CUSTOM_OPERATION_NOT_IMPLEMENTED` | warning | An operation ADF published to remote callers but implemented in Java |
| `DATASOURCE_NEEDS_CONFIGURATION` | warning | The database connection must be supplied by configuration; no credential was carried over |
| `ENDPOINTS_SHARE_ONE_QUERY` | warning | Two published URLs resolve to the same query, so the generated controllers are identical apart from their path |
| `ENDPOINT_DENIED_BY_DEFAULT` | warning | No grant covers this endpoint, so it was generated denied |
| `EXPERT_SQL_CARRIED_OVER` | warning | Hand-written SQL was carried across unchanged as a native query |
| `GROOVY_NOT_TRANSLATED` | warning | A Groovy expression was found and left untranslated |
| `JOINED_ATTRIBUTE_NOT_MAPPED` | warning | A view object joins several entities and this attribute belongs to one that is not the entity behind it, so it cannot be read from that... |
| `SCREEN_DERIVED_ENDPOINT` | warning | An endpoint generated for an ADF Faces application from what its screens read |
| `TYPE_NOT_PORTABLE` | warning | Attribute type has no equivalent outside ADF |
| `USER_GRANT_NOT_ROLE` | warning | Permissions are held by a named user rather than a role |
| `VALIDATION_NOT_TRANSLATED` | warning | A validation rule delegates to an expression and could not be turned into a constraint |
| `VERSION_COLUMN_UNSUPPORTED_TYPE` | warning | ADF used a column for optimistic locking whose type JPA cannot use as a version |
| `VIEW_NOT_DATABASE_BACKED` | warning | View object does not read from the database |
| `ADF_TYPE_MAPPED` | info | An Oracle runtime type was replaced by its Java equivalent |
| `CONTROLLER_GENERATED` | info | REST controller generated |
| `ENTITY_GENERATED` | info | JPA entity generated |
| `PROJECT_GENERATED` | info | Project scaffolding generated |
| `REPOSITORY_GENERATED` | info | Spring Data repository generated |
| `SECURITY_GENERATED` | info | Spring Security configuration generated |
| `VALIDATION_TRANSLATED` | info | A declarative ADF rule became a Bean Validation constraint with the same meaning |
| `VERSION_COLUMN_ADDED` | info | The application declared optimistic locking, so a version column was added |

## Severities

**error** — the generator failed. Nothing usable came out of it.

**skipped** — something was deliberately not generated, and the reason is given. The project
compiles without it. This is the severity to fail a build on when nothing should be left behind.

**warning** — generated, but a person has to finish or check it. A `TODO(adfmig:<CODE>)` marks
the place in the code, so searching for `TODO(adfmig:` finds every one of them.

**info** — a record of what was done, for anyone reconstructing how the output came about.

## Reading one

```json
{ "code": "QUERY_FILTER_NOT_CARRIED_OVER",
  "severity": "skipped",
  "source": "Model/src/com/example/model/views/StockLevelVO.xml",
  "subject": "StockLevelVO",
  "message": "The query was narrowed by: ... and that is not carried over",
  "action": "Read it through a relationship on the entity, or add it as a native @Query" }
```

`action` is the useful field in a pipeline: it says what to do, not just what happened.

