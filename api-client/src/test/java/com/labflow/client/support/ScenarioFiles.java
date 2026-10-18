package com.labflow.client.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads the scenario files of both check modules as plain text (spec 008 acceptance tests). It
 * knows the Feature line and its description, tags, scenario titles with their description, the
 * given / when / then lines, and the {@code Examples} tables; an outline is expanded into one
 * {@link Entry} per example row, with the row's values written into the title, the description
 * and the lines, as the report shows them. Nothing else of Gherkin is understood, on purpose.
 * Deliberately independent of the guards inside {@code api-tests} and {@code ui-tests}, so an
 * acceptance test over both families does not trust the code it is checking.
 */
public final class ScenarioFiles {

    /** Where the two modules keep their scenario files, seen from {@code api-client}. */
    public static final Path WORKFLOW = Path.of("..", "api-tests", "src", "test", "features");
    public static final Path SCREEN = Path.of("..", "ui-tests", "src", "test", "features");

    private static final List<String> STEP_WORDS = List.of("Given ", "When ", "Then ", "And ", "But ");

    private ScenarioFiles() {
    }

    /** One report entry: a scenario, or one row of an outline. */
    public record Entry(String family, String file, String topic, String topicDescription,
                        String subGroup, String name, int line, String description, List<String> lines) {
    }

    /** Every entry of a folder, files in name order, outline rows in file order. Fails loudly when there is none. */
    public static List<Entry> in(Path folder, String family) {
        if (!Files.isDirectory(folder)) {
            throw new IllegalStateException("Expected scenario files at " + folder.toAbsolutePath());
        }
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.list(folder)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".feature")).sorted().toList()) {
                entries.addAll(read(path, family));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + folder, e);
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("No scenario in " + folder.toAbsolutePath());
        }
        return entries;
    }

    private static final class Draft {
        List<String> tags;
        boolean outline;
        String title;
        int line;
        final List<String> description = new ArrayList<>();
        final List<String> lines = new ArrayList<>();
        final List<String> header = new ArrayList<>();
        final List<List<String>> rows = new ArrayList<>();
    }

    static List<Entry> read(Path file, String family) {
        List<String> source;
        try {
            source = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
        String topic = null;
        List<String> featureTags = new ArrayList<>();
        List<String> topicDescription = new ArrayList<>();
        List<Draft> drafts = new ArrayList<>();
        List<String> pendingTags = new ArrayList<>();
        Draft current = null;
        boolean inExamples = false;
        boolean inDocString = false;
        for (int i = 0; i < source.size(); i++) {
            String line = source.get(i).strip();
            if (line.startsWith("\"\"\"")) {
                inDocString = !inDocString;
                continue;
            }
            if (inDocString || line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("@")) {
                pendingTags.addAll(Arrays.asList(line.split("\\s+")));
            } else if (line.startsWith("Feature:")) {
                topic = line.substring("Feature:".length()).strip();
                featureTags.addAll(pendingTags);
                pendingTags.clear();
            } else if (line.startsWith("Scenario Outline:") || line.startsWith("Scenario:")) {
                current = new Draft();
                current.outline = line.startsWith("Scenario Outline:");
                current.title = line.substring(line.indexOf(':') + 1).strip();
                current.line = i + 1;
                current.tags = new ArrayList<>(featureTags);
                current.tags.addAll(pendingTags);
                pendingTags.clear();
                drafts.add(current);
                inExamples = false;
            } else if (line.startsWith("Examples:")) {
                inExamples = true;
                pendingTags.clear();
            } else if (line.startsWith("|")) {
                if (inExamples && current != null) {
                    List<String> cells = Arrays.stream(line.substring(1, line.length() - 1).split("\\|", -1)).map(String::strip).toList();
                    if (current.header.isEmpty()) {
                        current.header.addAll(cells);
                    } else {
                        current.rows.add(cells);
                    }
                }
            } else if (current == null) {
                topicDescription.add(line);
            } else if (STEP_WORDS.stream().anyMatch(line::startsWith)) {
                current.lines.add(line);
            } else if (current.lines.isEmpty()) {
                current.description.add(line);
            }
        }
        if (topic == null) {
            throw new IllegalStateException("No Feature: line in " + file);
        }
        List<Entry> entries = new ArrayList<>();
        String topicText = String.join(" ", topicDescription);
        for (Draft draft : drafts) {
            String subGroup = draft.tags.contains("@accepted") ? "Accepted" : draft.tags.contains("@refused") ? "Refused" : "(none)";
            if (!draft.outline) {
                entries.add(new Entry(family, file.getFileName().toString(), topic, topicText, subGroup, draft.title, draft.line,
                        String.join(" ", draft.description), List.copyOf(draft.lines)));
                continue;
            }
            for (List<String> row : draft.rows) {
                Map<String, String> values = new LinkedHashMap<>();
                for (int c = 0; c < draft.header.size(); c++) {
                    values.put(draft.header.get(c), row.get(c));
                }
                entries.add(new Entry(family, file.getFileName().toString(), topic, topicText, subGroup, fill(draft.title, values), draft.line,
                        fill(String.join(" ", draft.description), values), draft.lines.stream().map(l -> fill(l, values)).toList()));
            }
        }
        return entries;
    }

    private static String fill(String text, Map<String, String> values) {
        String filled = text;
        for (Map.Entry<String, String> value : values.entrySet()) {
            filled = filled.replace("<" + value.getKey() + ">", value.getValue());
        }
        return filled;
    }
}
