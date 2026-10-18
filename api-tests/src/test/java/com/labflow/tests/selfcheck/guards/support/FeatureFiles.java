package com.labflow.tests.selfcheck.guards.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads this module's feature files as plain text so the guards can check their tags and names
 * (spec 008). It is deliberately small: it knows tag lines, the {@code Feature:} line and the
 * scenario titles, and nothing else. Maven runs the checks with the module directory as the
 * working directory. Like {@code CheckSources}, it fails loudly when the folder is missing or
 * holds no feature file, so a guard cannot pass by scanning nothing. Copied between {@code api-tests}
 * and {@code ui-tests} for the same reason {@code CheckSources} is (no shared test module).
 */
public final class FeatureFiles {

    private static final Path FEATURES = Path.of("src", "test", "features");
    private static final List<String> SCENARIO_KEYWORDS = List.of("Scenario Outline:", "Scenario Template:", "Scenario:", "Example:");
    private static final List<String> OTHER_KEYWORDS = List.of("Rule:", "Background:");

    private FeatureFiles() {
    }

    /** One {@code Scenario} or {@code Scenario Outline}: its title, its own tags and the tags on its {@code Examples}. */
    public record Scenario(String name, int line, List<String> tags, List<String> exampleTags) {
    }

    /** One feature file: its name after {@code Feature:}, the tags on that line, its scenarios and any tag that is on no scenario or feature. */
    public record Feature(String file, String name, List<String> tags, List<Scenario> scenarios, List<String> strayTags) {
    }

    /** Every feature file of the module, sorted by file name. Fails loudly when there is none. */
    public static List<Feature> all() {
        if (!Files.isDirectory(FEATURES)) {
            throw new IllegalStateException("Expected the feature files at " + FEATURES.toAbsolutePath()
                    + " - a guard that scans nothing would pass without proving anything");
        }
        try (Stream<Path> paths = Files.list(FEATURES)) {
            List<Feature> features = paths.filter(path -> path.toString().endsWith(".feature")).sorted().map(FeatureFiles::read).toList();
            if (features.isEmpty()) {
                throw new IllegalStateException("No .feature files under " + FEATURES.toAbsolutePath());
            }
            return features;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the feature folder " + FEATURES, e);
        }
    }

    /** The scenarios tagged {@code @smoke}, as {@code Feature name › scenario name}, sorted. */
    public static List<String> smokeScenarios() {
        List<String> smoke = new ArrayList<>();
        for (Feature feature : all()) {
            for (Scenario scenario : feature.scenarios()) {
                if (scenario.tags().contains("@smoke")) {
                    smoke.add(feature.name() + " › " + scenario.name());
                }
            }
        }
        return smoke.stream().sorted().toList();
    }

    static Feature read(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
        String featureName = null;
        List<String> featureTags = new ArrayList<>();
        List<Scenario> scenarios = new ArrayList<>();
        List<String> stray = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        boolean inDocString = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.startsWith("\"\"\"") || line.startsWith("```")) {
                inDocString = !inDocString;
                continue;
            }
            if (inDocString || line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("@")) {
                String tagLine = line.contains(" #") ? line.substring(0, line.indexOf(" #")) : line;
                pending.addAll(Arrays.asList(tagLine.strip().split("\\s+")));
            } else if (line.startsWith("Feature:")) {
                featureName = line.substring("Feature:".length()).strip();
                featureTags.addAll(pending);
                pending.clear();
            } else if (keywordOf(line, SCENARIO_KEYWORDS) != null) {
                String keyword = keywordOf(line, SCENARIO_KEYWORDS);
                scenarios.add(new Scenario(line.substring(keyword.length()).strip(), i + 1, List.copyOf(pending), new ArrayList<>()));
                pending.clear();
            } else if (line.startsWith("Examples:") || line.startsWith("Scenarios:")) {
                if (scenarios.isEmpty()) {
                    stray.addAll(pending);
                } else {
                    scenarios.get(scenarios.size() - 1).exampleTags().addAll(pending);
                }
                pending.clear();
            } else {
                stray.addAll(pending);
                pending.clear();
            }
        }
        stray.addAll(pending);
        if (featureName == null) {
            throw new IllegalStateException("No Feature: line in " + file);
        }
        return new Feature(file.getFileName().toString(), featureName, featureTags, scenarios, stray);
    }

    private static String keywordOf(String line, List<String> keywords) {
        return keywords.stream().filter(line::startsWith).findFirst().orElse(null);
    }
}
