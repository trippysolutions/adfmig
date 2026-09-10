package com.adfmig.parser;

import com.adfmig.core.model.CustomJavaClass;
import com.adfmig.core.model.CustomMethod;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SuperExpr;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a hand-written ADF Java class and describes what is in it.
 *
 * <p>Knowing that a custom class exists is enough to warn about; it is not enough to price. An
 * override that applies a view criteria and returns a count is an afternoon's work. One that walks
 * a row set issuing its own SQL through the ADF transaction is a week. Scoring both the same makes
 * every estimate wrong.
 *
 * <p>Nothing is executed and nothing is rewritten. The class is parsed to describe it, and where
 * the body matches a shape with an exact Spring equivalent that is named so the generator and the
 * report can both say so.
 */
final class CustomJavaParser {

    /**
     * ADF lifecycle hooks. Overriding one means the class runs inside ADF's own machinery at a
     * point Spring has no equivalent of, so the behaviour has to be re-hosted rather than copied.
     */
    private static final Set<String> LIFECYCLE = Set.of(
            "doDML", "prepareForDML", "validateEntity", "beforeCommit", "afterCommit",
            "beforeRollback", "afterRollback", "create", "remove", "lock", "refresh",
            "executeQueryForCollection", "createRowFromResultSet", "hasNextForCollection",
            "prepareRowSetForQuery", "beforeCommitInvoke", "afterCommitInvoke", "activateState",
            "passivateState", "prepareSession", "afterConnect");

    /** ADF runtime calls: the part of a method that cannot be carried across unchanged. */
    private static final Set<String> ADF_API = Set.of(
            "getDBTransaction", "createRow", "insertRow", "removeCurrentRow", "postChanges",
            "executeQuery", "executeQueryForCollection", "setWhereClause", "setWhereClauseParams",
            "applyViewCriteria", "createViewCriteria", "getViewCriteria", "ensureVariableManager",
            "setVariableValue", "getVariableValue", "getEstimatedRowCount", "createRowSetIterator",
            "getRowSetIterator", "findByKey", "getCurrentRow", "setAttribute", "getAttribute",
            "getApplicationModule", "createPreparedStatement", "getSequenceNumber");

    /** ADF base classes, which say what kind of component the class belongs to. */
    private static final String ENTITY_BASE = "EntityImpl";
    private static final String VIEW_BASE = "ViewObjectImpl";
    private static final String ROW_BASE = "ViewRowImpl";
    private static final String MODULE_BASE = "ApplicationModuleImpl";

    Optional<CustomJavaClass> parse(Path file, String relativePath) {
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception utf8) {
            try {
                // ADF sources predate any encoding discipline; see XmlEncodings for the same story.
                source = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            } catch (Exception fatal) {
                return Optional.empty();
            }
        }

        CompilationUnit unit;
        try {
            unit = StaticJavaParser.parse(source);
        } catch (RuntimeException e) {
            // Unparseable Java is a finding, not a reason to abandon the scan. It is reported by
            // the caller as a component that could not be described.
            return Optional.empty();
        }

        Optional<ClassOrInterfaceDeclaration> declaration = unit.findFirst(ClassOrInterfaceDeclaration.class);
        if (declaration.isEmpty()) return Optional.empty();

        ClassOrInterfaceDeclaration type = declaration.get();
        String packageName = unit.getPackageDeclaration()
                .map(p -> p.getNameAsString() + ".")
                .orElse("");

        return Optional.of(new CustomJavaClass(
                packageName + type.getNameAsString(),
                kindOf(type),
                type.getMethods().stream().map(CustomJavaParser::describe).toList(),
                source.split("\n", -1).length,
                relativePath));
    }

    private static CustomJavaClass.Kind kindOf(ClassOrInterfaceDeclaration type) {
        for (var extended : type.getExtendedTypes()) {
            String name = extended.getNameAsString();
            if (name.endsWith(ENTITY_BASE)) return CustomJavaClass.Kind.ENTITY;
            if (name.endsWith(VIEW_BASE)) return CustomJavaClass.Kind.VIEW_OBJECT;
            if (name.endsWith(ROW_BASE)) return CustomJavaClass.Kind.VIEW_ROW;
            if (name.endsWith(MODULE_BASE)) return CustomJavaClass.Kind.APPLICATION_MODULE;
        }
        return CustomJavaClass.Kind.UNKNOWN;
    }

    private static CustomMethod describe(MethodDeclaration method) {
        Set<String> calls = new LinkedHashSet<>();
        method.findAll(MethodCallExpr.class).forEach(call -> {
            if (ADF_API.contains(call.getNameAsString())) calls.add(call.getNameAsString());
        });

        String name = method.getNameAsString();
        List<String> parameterTypes = method.getParameters().stream()
                .map(p -> p.getType().asString())
                .toList();

        int lines = method.getBody()
                .map(body -> body.toString().split("\n", -1).length)
                .orElse(0);

        if (isPureSuperCall(method)) {
            // JDeveloper writes these when a developer asks to override a hook and then changes
            // nothing. The override customises no behaviour, so there is nothing to carry over.
            return new CustomMethod(name, method.getType().asString(), parameterTypes,
                    null, List.of(), "an override that only calls super, customising nothing", lines);
        }

        return new CustomMethod(
                name,
                method.getType().asString(),
                parameterTypes,
                LIFECYCLE.contains(name) ? name : null,
                List.copyOf(calls),
                recognisePattern(calls, method),
                lines);
    }

    /** True when the body is nothing but a call to the same method on the superclass. */
    private static boolean isPureSuperCall(MethodDeclaration method) {
        var statements = method.getBody().map(b -> b.getStatements()).orElse(null);
        if (statements == null || statements.size() != 1) return false;

        return statements.get(0).findAll(MethodCallExpr.class).stream()
                .anyMatch(call -> call.getScope().filter(SuperExpr.class::isInstance).isPresent()
                        && call.getNameAsString().equals(method.getNameAsString()));
    }

    /**
     * Names the shapes that appear again and again in ADF code and have an exact equivalent.
     *
     * <p>Only shapes certain enough to state are named. Anything else is left unrecognised, which
     * costs an estimate a little accuracy; naming one wrongly would tell a customer that a week of
     * work is an afternoon.
     */
    private static String recognisePattern(Set<String> calls, MethodDeclaration method) {
        boolean appliesCriteria = calls.contains("applyViewCriteria") || calls.contains("getViewCriteria");
        boolean bindsVariables = calls.contains("setVariableValue");
        boolean runs = calls.contains("executeQuery");

        if (appliesCriteria && runs && calls.contains("getEstimatedRowCount")) {
            return "applies a view criteria and returns the row count "
                    + "(a count query with a Specification)";
        }
        if (appliesCriteria && bindsVariables && runs) {
            return "applies a view criteria with bind variables "
                    + "(a Specification and a repository call)";
        }
        if (calls.contains("getSequenceNumber")) {
            return "reads a database sequence (a JPA sequence generator)";
        }
        if (calls.isEmpty() && method.getBody()
                .map(body -> body.getStatements().size() <= 1)
                .orElse(false)) {
            return "a single statement touching nothing ADF-specific";
        }
        return null;
    }
}
