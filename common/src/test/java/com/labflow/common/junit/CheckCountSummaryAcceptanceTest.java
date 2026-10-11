// @layer: unit
// @spec: 005-reliable-isolated-execution
// @regression
package com.labflow.common.junit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Acceptance check for functional-spec.md section 2.1 (a label that matches nothing): the
 * end-of-run summary line states the number of checks run and the labels asked for, so a run
 * that selected nothing says "0" in plain words. Needs no lab.
 */
class CheckCountSummaryAcceptanceTest {

    @Test
    @DisplayName("A run that matched no check says 0 checks ran and names the label that was asked for")
    void zeroChecksIsStatedClearly() {
        assertEquals("Lab checks run: 0 (labels: nosuch)", CheckCountListener.summary(0, "nosuch"));
    }

    @Test
    @DisplayName("A run without a label says how many checks ran and that all labels were in play, so a missing label is not mistaken for zero")
    void noLabelMeansAllLabels() {
        assertEquals("Lab checks run: 25 (labels: all)", CheckCountListener.summary(25, null));
        assertEquals("Lab checks run: 25 (labels: all)", CheckCountListener.summary(25, "  "));
    }
}
