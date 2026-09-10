package com.adfmig.core.model;

import java.util.List;

/**
 * A REST resource declared by an ADF BC application (12.2.1 and later).
 *
 * <p>ADF reuses the binding-layer {@code pageDefinition} format for these rather than defining a
 * dedicated schema; {@code usageMode="RESTClient"} is what distinguishes one.
 *
 * <p>These are the highest-value artifacts in a migration. The application already has an HTTP
 * contract, so the Spring Boot replacement can preserve it exactly and be verified response by
 * response against the original.
 *
 * @param id            the resource definition's id
 * @param fqn           fully qualified name of the defining page definition
 * @param viewUsage     the application module view instance it binds to, e.g. {@code EmployeesView1}
 * @param dataControl   the data control id, which resolves to the owning application module
 * @param collections   collection names exposed by the resource — these become the JSON payload keys
 * @param operations    custom operations callable on the resource
 * @param rangeSize     the page size ADF served this resource at. ADF BC REST paginates every
 *                      collection and returns an envelope rather than a bare array, so a
 *                      replacement that returns the whole table has a different response shape,
 *                      not merely a different length.
 * @param sourcePath    path of the defining XML, relative to the application root
 */
public record RestResource(
        String id,
        String fqn,
        String viewUsage,
        String dataControl,
        List<String> collections,
        List<Operation> operations,
        Integer rangeSize,
        String sourcePath) {

    /** ADF's own default when a resource declares no range size. */
    public static final int DEFAULT_RANGE_SIZE = 25;

    public int pageSize() {
        return rangeSize == null || rangeSize <= 0 ? DEFAULT_RANGE_SIZE : rangeSize;
    }

    /**
     * A custom operation exposed on the resource.
     *
     * @param viewObjectMethod true when the implementation lives on the view object rather than on
     *                         the application module, which decides where the generated service
     *                         method belongs
     * @param params           declared parameters, with types
     */
    public record Operation(
            String name,
            boolean viewObjectMethod,
            String instanceName,
            List<Param> params) {

        public String signature() {
            String args = params.stream()
                    .map(p -> p.name() + ": " + Types.simple(p.javaType()))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            return name + "(" + args + ")";
        }
    }

    public record Param(String name, String javaType) {}
}
