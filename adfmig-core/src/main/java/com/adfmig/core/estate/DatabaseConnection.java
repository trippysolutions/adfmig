package com.adfmig.core.estate;

/**
 * How an application module reaches its database, as declared in {@code bc4j.xcfg}.
 *
 * <p>ADF declares this in two quite different ways, and a survey that handles only one will report
 * that most of an estate has no database at all:
 *
 * <ul>
 *   <li>A {@code ConnectionDefinition} carrying host, port, SID and user. This is the development
 *       form, written by JDeveloper against a local database.
 *   <li>A {@code JDBCDataSource} naming a JNDI datasource such as
 *       {@code java:comp/env/jdbc/HrDS}. This is the deployed form, and the one production
 *       applications use — the real connection details live in the WebLogic domain, not here.
 * </ul>
 *
 * <p>Credentials are deliberately not captured. The development form holds an obfuscated password,
 * and a migration tool has no business copying customer credentials into a report or a JSON export.
 *
 * @param applicationModule the application module this configuration serves
 * @param lockingMode       ADF's {@code jbo.locking.mode}, when declared. This decides whether the
 *                          generated JPA entity needs a {@code @Version} column: an application
 *                          running optimistic locking is already relying on a change indicator,
 *                          and the replacement must keep enforcing it or concurrent updates start
 *                          overwriting one another.
 */
public record DatabaseConnection(
        String name,
        Kind kind,
        String host,
        String port,
        String sid,
        String user,
        String dataSource,
        String applicationModule,
        String lockingMode,
        String sourcePath) {

    public enum Kind {
        /** Direct JDBC connection details, written for development. */
        JDBC_URL,
        /** A JNDI datasource name resolved by the application server. */
        DATA_SOURCE
    }

    public static DatabaseConnection jdbc(String name, String host, String port, String sid,
                                          String user, String applicationModule,
                                          String lockingMode, String sourcePath) {
        return new DatabaseConnection(name, Kind.JDBC_URL, host, port, sid, user, null,
                applicationModule, lockingMode, sourcePath);
    }

    public static DatabaseConnection dataSource(String name, String dataSource,
                                                String applicationModule,
                                                String lockingMode, String sourcePath) {
        return new DatabaseConnection(name, Kind.DATA_SOURCE, null, null, null, null,
                normaliseDataSource(dataSource), applicationModule, lockingMode, sourcePath);
    }

    /** True when the application relies on optimistic locking that the replacement must preserve. */
    public boolean isOptimisticLocking() {
        return "optimistic".equalsIgnoreCase(lockingMode);
    }

    /**
     * Identifies the schema this connection reaches, for detecting applications that write the
     * same tables. See {@link AdfEstate#schemaConflicts()}.
     *
     * <p>For a datasource this is a match on <em>name</em>, not on a verified database. Two
     * applications naming {@code jdbc/HrDS} almost always share a schema, but confirming it means
     * looking at the WebLogic domain the tool cannot see, so the finding is worth raising and
     * worth verifying rather than asserting.
     */
    public String schemaIdentity() {
        if (kind == Kind.DATA_SOURCE) return dataSource;
        return lower(user) + "@" + lower(host) + ":" + or(port) + "/" + lower(sid);
    }

    /** {@code java:comp/env/jdbc/HrDS} and {@code jdbc/HrDS} name the same datasource. */
    private static String normaliseDataSource(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        int index = trimmed.indexOf("jdbc/");
        return index < 0 ? trimmed : trimmed.substring(index);
    }

    private static String lower(String value) {
        return value == null ? "?" : value.toLowerCase();
    }

    private static String or(String value) {
        return value == null ? "?" : value;
    }
}
