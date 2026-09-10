package com.adfmig.parser;

import com.adfmig.core.model.GroovyExpression;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a {@code .bcs} file: the Groovy expressions belonging to one business component.
 *
 * <p>The file is not XML. It is Groovy source in which ADF has annotated every expression with its
 * role and target attribute:
 *
 * <pre>{@code
 * @ValidatorExpression(name="SalaryRule0", attributeName="Salary")
 * def Salary_SalaryRule0_ValidationRuleScript_ValidationRule()
 * {
 *   if (newValue < Jobs.MinSalary) { return false }
 *   return true
 * }
 * }</pre>
 *
 * <p>The body is captured verbatim. Nothing here interprets Groovy — the point is to reproduce
 * each expression faithfully beside the stub that replaces it, so whoever finishes the migration
 * can see the original and the gap side by side.
 */
final class GroovyScriptParser {

    private static final Pattern ANNOTATION =
            Pattern.compile("@(\\w+)\\s*\\(([^)]*)\\)\\s*(?:@\\w+\\s*\\([^)]*\\)\\s*)*def\\s+(\\w+)\\s*\\([^)]*\\)");

    private static final Pattern ARGUMENT =
            Pattern.compile("(\\w+)\\s*=\\s*\"([^\"]*)\"");

    List<GroovyExpression> parse(Path file, String relativePath) {
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            try {
                // These are developer-written files and predate any encoding discipline.
                source = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            } catch (IOException fatal) {
                return List.of();
            }
        }

        List<GroovyExpression> expressions = new ArrayList<>();
        Matcher matcher = ANNOTATION.matcher(source);
        while (matcher.find()) {
            Map<String, String> arguments = arguments(matcher.group(2));
            String body = bodyAfter(source, matcher.end());
            expressions.add(new GroovyExpression(
                    matcher.group(1),
                    arguments.get("attributeName"),
                    arguments.get("name"),
                    matcher.group(3),
                    body,
                    relativePath));
        }
        return List.copyOf(expressions);
    }

    private static Map<String, String> arguments(String raw) {
        Map<String, String> arguments = new LinkedHashMap<>();
        Matcher matcher = ARGUMENT.matcher(raw);
        while (matcher.find()) arguments.put(matcher.group(1), matcher.group(2));
        return arguments;
    }

    /**
     * Captures the method body by matching braces, so an expression containing its own braces —
     * an {@code if} block, a closure — is not truncated at the first closing brace.
     */
    private static String bodyAfter(String source, int from) {
        int open = source.indexOf('{', from);
        if (open < 0) return "";

        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) {
                return source.substring(open + 1, i).strip();
            }
        }
        return source.substring(open + 1).strip();
    }
}
