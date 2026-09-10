package com.adfmig.core.model;

/**
 * One Groovy expression belonging to a business component, as ADF extracts them into the
 * component's {@code .bcs} companion file.
 *
 * <p>Groovy is the part of ADF a generator cannot translate. It is arbitrary code with access to
 * the ADF runtime, related entities and the security context, and guessing at its meaning would
 * produce something that looks finished and is subtly wrong.
 *
 * <p>What makes it tractable is that ADF annotates every expression with its role and the
 * attribute it applies to, so each one can be identified exactly, placed correctly in the
 * generated file, and reproduced verbatim beside the stub that replaces it.
 *
 * @param kind          the annotation ADF wrote, e.g. {@code ValidatorExpression}
 * @param attributeName the attribute the expression applies to
 * @param validatorName the rule it belongs to, for validation expressions
 * @param methodName    the generated Groovy method name
 * @param body          the expression source, verbatim and unmodified
 */
public record GroovyExpression(
        String kind,
        String attributeName,
        String validatorName,
        String methodName,
        String body,
        String sourcePath) {

    /** A default or calculated value for an attribute. */
    public boolean isValueExpression() {
        return "TransientValueExpression".equals(kind);
    }

    /** A validation rule's condition. */
    public boolean isValidationRule() {
        return "ValidatorExpression".equals(kind);
    }

    /** The guard deciding whether a validation rule applies at all. */
    public boolean isValidationCondition() {
        return "ValidatorConditionExpression".equals(kind);
    }

    /** Interpolation for a validation message, rather than business logic. */
    public boolean isMessageParameter() {
        return "MessageParameterExpression".equals(kind);
    }

    /** How to describe this expression in a diagnostic. */
    public String describe() {
        if (validatorName != null) return attributeName + " / " + validatorName;
        return attributeName == null ? methodName : attributeName;
    }

    /**
     * Recognises the one idiom common enough to be worth naming: reading the next value of a
     * database sequence, which maps exactly onto a JPA sequence generator.
     *
     * @return the sequence name, or {@code null} when this is not that idiom
     */
    public String sequenceName() {
        if (body == null) return null;
        var matcher = java.util.regex.Pattern
                .compile("SequenceImpl\\s*\\(\\s*[\"']([^\"']+)[\"']")
                .matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }
}
