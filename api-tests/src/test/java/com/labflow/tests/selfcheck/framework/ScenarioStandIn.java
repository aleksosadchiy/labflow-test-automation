package com.labflow.tests.selfcheck.framework;

import io.cucumber.java.Scenario;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.List;

/**
 * A stand-in for Cucumber's {@link Scenario}, for the framework's own tests of the hooks.
 *
 * <p>{@code Scenario} is a final class whose only constructor is package-private and takes the
 * engine's {@code TestCaseState} interface. A nested Cucumber run would exercise the hooks too, but
 * it would also need a feature file that fails on purpose and would be reported as a failed test of
 * the suite. Here the state is a small {@link Proxy} that answers the few questions the hooks ask
 * (name, line, file, tags, failed or not) and nothing else, and the real {@code Scenario} wraps it,
 * so the hooks run their real code against an object of the type they declare.
 */
public final class ScenarioStandIn {

    private static final String STATE = "io.cucumber.core.backend.TestCaseState";

    private ScenarioStandIn() {
    }

    /** A scenario of the given feature file (a classpath resource such as {@code features/x.feature}). */
    public static Scenario of(String featureResource, String name, int line, boolean failed) {
        return of(featureResource, name, line, failed, List.of("@api", "@refused"));
    }

    /** The same, with the tags the scenario carries (its own and those of its feature). */
    public static Scenario of(String featureResource, String name, int line, boolean failed, List<String> tags) {
        try {
            Class<?> state = Class.forName(STATE);
            InvocationHandler answers = (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> name;
                case "getLine" -> line;
                case "getUri" -> URI.create("classpath:" + featureResource);
                case "getId" -> "stand-in-" + name;
                case "getSourceTagNames" -> tags;
                case "isFailed" -> failed;
                default -> throw new UnsupportedOperationException("The stand-in does not answer " + method.getName());
            };
            Object delegate = Proxy.newProxyInstance(state.getClassLoader(), new Class<?>[] {state}, answers);
            Constructor<Scenario> constructor = Scenario.class.getDeclaredConstructor(state);
            constructor.setAccessible(true);
            return constructor.newInstance(delegate);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not build a stand-in for Cucumber's Scenario", e);
        }
    }
}
