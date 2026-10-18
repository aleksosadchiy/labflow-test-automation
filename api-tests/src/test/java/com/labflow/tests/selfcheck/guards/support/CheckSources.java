package com.labflow.tests.selfcheck.guards.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds the source files of the workflow checks so the guard tests can read them as plain text.
 * The checks are Gherkin scenarios (spec 008), so their code is the step definitions
 * ({@code steps}), the hooks ({@code hooks}) and the world class ({@code world/LabWorld}). Maven runs the checks with the
 * module directory ({@code api-tests}) as the working directory, so paths are relative to it.
 */
public final class CheckSources {

    private static final Path CHECKS_ROOT = Path.of("src", "test", "java", "com", "labflow", "tests");
    private static final Path STEPS = CHECKS_ROOT.resolve("steps");
    private static final Path HOOKS = CHECKS_ROOT.resolve("hooks");
    private static final Path WORLD = CHECKS_ROOT.resolve("world").resolve("LabWorld.java");

    private CheckSources() {
    }

    /** The step definition classes, sorted. Fails loudly when the folder is missing or empty. */
    public static List<Path> stepFiles() {
        return withoutSelfChecks(javaFilesUnder(STEPS), STEPS);
    }

    /** The hook classes (not the tests of them), sorted. Fails loudly when the folder is missing or empty. */
    public static List<Path> hookFiles() {
        return withoutSelfChecks(javaFilesUnder(HOOKS), HOOKS);
    }

    /** A hooks folder may also hold the framework's own tests of a hook (named {@code ...Test.java}); those are not check code. */
    private static List<Path> withoutSelfChecks(List<Path> files, Path folder) {
        List<Path> code = files.stream().filter(file -> !file.getFileName().toString().endsWith("Test.java")).toList();
        if (code.isEmpty()) {
            throw new IllegalStateException("No check code under " + folder.toAbsolutePath()
                    + " - a guard that scans nothing would pass without proving anything");
        }
        return code;
    }

    /** The world class that carries one scenario's state. Fails loudly when it is missing. */
    public static Path worldFile() {
        if (!Files.isRegularFile(WORLD)) {
            throw new IllegalStateException("Expected the world class at " + WORLD.toAbsolutePath()
                    + " - a guard that scans nothing would pass without proving anything");
        }
        return WORLD;
    }

    /** Every {@code .java} file of the checks (steps, hooks and the world), sorted. A guard cannot pass by scanning nothing. */
    public static List<Path> checkFiles() {
        List<Path> files = new ArrayList<>(stepFiles());
        files.addAll(hookFiles());
        files.add(worldFile());
        return files.stream().sorted().toList();
    }

    /** Every {@code .java} file below the folder, including sub-folders; fails loudly when the folder is missing or empty. */
    public static List<Path> javaFilesUnder(Path folder) {
        if (!Files.isDirectory(folder)) {
            throw new IllegalStateException("Expected a source folder at " + folder.toAbsolutePath()
                    + " - a guard that scans nothing would pass without proving anything");
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            List<Path> files = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
            if (files.isEmpty()) {
                throw new IllegalStateException("No .java files under " + folder.toAbsolutePath());
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the source folder " + folder, e);
        }
    }

    /** Every line of the given files that contains the text, as {@code file:line: text}. */
    public static List<String> linesContaining(List<Path> files, String text) {
        List<String> found = new ArrayList<>();
        for (Path file : files) {
            List<String> lines;
            try {
                lines = Files.readAllLines(file);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file, e);
            }
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(text)) {
                    found.add(file.getFileName() + ":" + (i + 1) + ": " + lines.get(i).trim());
                }
            }
        }
        return found;
    }
}
