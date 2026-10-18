package com.labflow.ui.tests.selfcheck.guards.support;

import io.cucumber.plugin.event.Status;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.engine.TestExecutionResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the module's feature files through the Cucumber engine with {@code cucumber.execution.dry-run=true}
 * (spec 008, guards of technical-considerations 2.8): every Gherkin line is matched against the step
 * definitions, but no step is executed, so it needs no lab and no browser. The engine has no option
 * that lists unused step definitions, so they are computed here: a small listening plugin
 * ({@link DryRunRecorder}) records every step definition the engine registered and every one a line
 * matched; the difference is the unused ones. A line that matches none is reported with its file
 * and line number (an undefined step), and so is a line that matches two (ambiguous).
 *
 * <p>The run is made through the JUnit Platform launcher with the module's own settings
 * ({@code junit-platform.properties}: the glue packages) and four overrides: dry run on, no parallel
 * execution, and the report plugin replaced by the recorder, so nothing reaches the Allure report.
 * The launcher is created without auto-registered listeners, so the lab pre-flight and the run's
 * counters stay out of this nested run. Results of the default selection (the whole {@code features}
 * package) are computed once per JVM.
 */
public final class DryRun {

    private static final Object LOCK = new Object();
    private static Collector current;
    private static Result wholeFeatures;

    private DryRun() {
    }

    /** One Gherkin line that matched no step definition or more than one: its file, its line and its text. */
    public record Problem(Status status, String file, int line, String text) {
        @Override
        public String toString() {
            return status + ": " + file + ":" + line + " '" + text.strip() + "'";
        }
    }

    /** One scenario entry as the engine expands it (an outline row is one): the file, the line and the name. */
    public record Entry(String file, int line, String name) {
    }

    /** What a dry run found. */
    public record Result(Set<String> definitions, Set<String> used, List<Problem> problems, List<Entry> entries, List<String> engineFailures) {

        /** Step definitions that no line of the run matched. */
        public List<String> unused() {
            return definitions.stream().filter(definition -> !used.contains(definition)).sorted().toList();
        }
    }

    /** The dry run of every feature file of the module. */
    public static Result ofTheFeatures() {
        synchronized (LOCK) {
            if (wholeFeatures == null) {
                wholeFeatures = run(DiscoverySelectors.selectPackage("features"));
            }
            return wholeFeatures;
        }
    }

    /** A dry run of the given selection only, for example one feature file written for a test. */
    public static Result of(DiscoverySelector... selectors) {
        synchronized (LOCK) {
            return run(selectors);
        }
    }

    static Collector currentCollector() {
        return current;
    }

    private static Result run(DiscoverySelector... selectors) {
        Collector collector = new Collector();
        current = collector;
        try {
            Launcher launcher = LauncherFactory.create(LauncherConfig.builder()
                    .enableLauncherSessionListenerAutoRegistration(false)
                    .enableTestExecutionListenerAutoRegistration(false)
                    .enableLauncherDiscoveryListenerAutoRegistration(false)
                    .build());
            launcher.execute(LauncherDiscoveryRequestBuilder.request()
                            .selectors(selectors)
                            .filters(EngineFilter.includeEngines("cucumber"))
                            .configurationParameter("cucumber.execution.dry-run", "true")
                            .configurationParameter("cucumber.execution.parallel.enabled", "false")
                            .configurationParameter("cucumber.plugin", DryRunRecorder.class.getName())
                            .build(),
                    new TestExecutionListener() {
                        @Override
                        public void executionFinished(TestIdentifier test, TestExecutionResult result) {
                            if (result.getStatus() == TestExecutionResult.Status.FAILED) {
                                collector.engineFailure(test.getDisplayName() + ": "
                                        + result.getThrowable().map(Throwable::getMessage).orElse("failed"));
                            }
                        }
                    });
        } finally {
            current = null;
        }
        return collector.result();
    }

    /** The recorder's notebook; thread-safe, because the engine may deliver events from several threads. */
    static final class Collector {
        private final Set<String> definitions = ConcurrentHashMap.newKeySet();
        private final Set<String> used = ConcurrentHashMap.newKeySet();
        private final List<Problem> problems = Collections.synchronizedList(new ArrayList<>());
        private final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());
        private final List<String> engineFailures = Collections.synchronizedList(new ArrayList<>());

        void defined(String definition) {
            definitions.add(definition);
        }

        void used(String definition) {
            used.add(definition);
        }

        void problem(Status status, String uri, int line, String text) {
            problems.add(new Problem(status, fileOf(uri), line, text));
        }

        void scenario(String uri, int line, String name) {
            entries.add(new Entry(fileOf(uri), line, name));
        }

        void engineFailure(String message) {
            engineFailures.add(message);
        }

        Result result() {
            List<Problem> sortedProblems = new ArrayList<>(problems);
            sortedProblems.sort(java.util.Comparator.comparing(Problem::file).thenComparingInt(Problem::line));
            List<Entry> sortedEntries = new ArrayList<>(entries);
            sortedEntries.sort(java.util.Comparator.comparing(Entry::file).thenComparingInt(Entry::line).thenComparing(Entry::name));
            return new Result(new TreeSet<>(definitions), new TreeSet<>(used), sortedProblems, sortedEntries, List.copyOf(engineFailures));
        }

        /** {@code classpath:features/01-x.feature} or {@code file:/tmp/x.feature} become the file name. */
        private static String fileOf(String uri) {
            return uri.substring(Math.max(uri.lastIndexOf('/'), uri.lastIndexOf(':')) + 1);
        }
    }
}
