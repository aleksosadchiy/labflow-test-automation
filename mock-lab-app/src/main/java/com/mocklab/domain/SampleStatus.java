package com.mocklab.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * The lab workflow's state-machine stages, in declaration order matching the
 * order a {@link Sample} moves through them. The actual transition logic
 * lives in {@code ProcessingJobService} (a later slice) — this enum only
 * defines the stages themselves.
 *
 * <p>Wire/storage values are the enum {@link #name()}; {@link #displayName()}
 * is the human-readable stage name used in error messages.
 */
public enum SampleStatus {
    ACCESSIONED("Accessioned"),
    READY_FOR_PROCESSING("Ready for Processing"),
    PROCESSING("Processing"),
    SEQUENCING("Sequencing"),
    BIOINFORMATICS("Bioinformatics"),
    QUALITY_CHECK("Quality Check"),
    RESULT_READY("Result Ready"),
    REPORT_GENERATED("Report Generated");

    private final String displayName;

    SampleStatus(String displayName) {
        this.displayName = displayName;
    }

    /** The stage name as users see it, e.g. "Ready for Processing". */
    public String displayName() {
        return displayName;
    }

    /**
     * Resolves a stage from user input. Case-insensitive, ignores leading and
     * trailing whitespace, and treats runs of spaces between words as a single
     * underscore, so {@code PROCESSING}, {@code processing},
     * {@code Ready for Processing} and {@code READY_FOR_PROCESSING} all
     * resolve. Anything else (null, blank, unknown, partial) yields
     * {@link Optional#empty()} — the caller decides which error to raise.
     */
    public static Optional<SampleStatus> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalised = value.strip().replaceAll(" +", "_").toUpperCase(Locale.ROOT);
        for (SampleStatus status : values()) {
            if (status.name().equals(normalised)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
