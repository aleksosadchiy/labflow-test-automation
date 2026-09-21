package com.labflow.tests.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds the source files of the workflow checks (the {@code happy} and {@code rejection}
 * packages) so the guard tests can read them as plain text. Maven runs the checks with the
 * module directory ({@code api-tests}) as the working directory, so paths are relative to it.
 */
public final class CheckSources {

    private static final Path CHECKS_ROOT = Path.of("src", "test", "java", "com", "labflow", "tests");
    private static final List<String> CHECK_PACKAGES = List.of("happy", "rejection");

    private CheckSources() {
    }

    /** Every {@code .java} file of the checks, sorted. Fails loudly when none is found, so a guard cannot pass by scanning nothing. */
    public static List<Path> checkFiles() {
        List<Path> files = new ArrayList<>();
        for (String checkPackage : CHECK_PACKAGES) {
            files.addAll(javaFilesUnder(CHECKS_ROOT.resolve(checkPackage)));
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("No check sources found under " + CHECKS_ROOT.toAbsolutePath()
                    + " - a guard that scans nothing would pass without proving anything");
        }
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
