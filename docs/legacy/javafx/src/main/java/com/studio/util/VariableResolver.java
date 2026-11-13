package com.studio.util;

import com.studio.model.Assertion;
import com.studio.model.TestStep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Variable Pipeline & Interpolation Engine (inspired by Automa & Testsigma).
 * Resolves expressions formatted as {{variable_name}} or ${variable_name} against
 * active test data profiles, row iterations, and system environment variables.
 */
public class VariableResolver {

    private static final Pattern VAR_PATTERN = Pattern.compile("(?:\\{\\{([a-zA-Z0-9_.-]+)\\}\\}|\\$\\{([a-zA-Z0-9_.-]+)\\})");

    /**
     * Resolves all variable occurrences in the template string using the provided context map.
     *
     * @param template input template string
     * @param context key-value variables
     * @return interpolated string with all recognized tokens replaced
     */
    public static String resolve(String template, Map<String, String> context) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String replacement = null;

            if (context != null && context.containsKey(varName)) {
                replacement = context.get(varName);
            } else if (varName.toUpperCase().startsWith("ENV.") && varName.length() > 4) {
                String envKey = varName.substring(4);
                replacement = System.getenv(envKey);
            }

            if (replacement == null) {
                replacement = matcher.group(0); // keep original token if unresolved
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolves dynamic variables within a list of TestSteps.
     */
    public static List<TestStep> resolveSteps(List<TestStep> steps, Map<String, String> context) {
        if (steps == null) return Collections.emptyList();
        if (context == null || context.isEmpty()) return steps;

        List<TestStep> resolved = new ArrayList<>();
        for (TestStep s : steps) {
            TestStep copy = new TestStep(
                    s.getStepNumber(),
                    s.getAction(),
                    resolve(s.getTarget(), context),
                    resolve(s.getTestData(), context),
                    resolve(s.getDescription(), context),
                    resolve(s.getExpectedResult(), context)
            );
            resolved.add(copy);
        }
        return resolved;
    }

    /**
     * Resolves dynamic variables within a list of Assertions.
     */
    public static List<Assertion> resolveAssertions(List<Assertion> assertions, Map<String, String> context) {
        if (assertions == null) return Collections.emptyList();
        if (context == null || context.isEmpty()) return assertions;

        List<Assertion> resolved = new ArrayList<>();
        for (Assertion a : assertions) {
            Assertion copy = new Assertion();
            copy.setType(a.getType());
            copy.setStatus(a.getStatus());
            copy.setExact(a.isExact());
            copy.setTimeoutMs(a.getTimeoutMs());
            copy.setLocator(resolve(a.getLocator(), context));
            copy.setValue(resolve(a.getValue(), context));
            copy.setAttribute(resolve(a.getAttribute(), context));
            copy.setExpectedValue(resolve(a.getExpectedValue(), context));
            copy.setOperator(a.getOperator());
            copy.setAltContains(resolve(a.getAltContains(), context));
            resolved.add(copy);
        }
        return resolved;
    }
}

