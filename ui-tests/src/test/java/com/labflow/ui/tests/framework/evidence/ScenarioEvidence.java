package com.labflow.ui.tests.framework.evidence;

import java.nio.file.Path;

/**
 * Lets the Cucumber hooks (another package) hand a failed scenario's evidence folder to the
 * package-private {@link AllureEvidence}, which stays as it is.
 */
public final class ScenarioEvidence {

    private ScenarioEvidence() {
    }

    public static void attach(Path failureFolder) {
        AllureEvidence.attach(failureFolder);
    }
}
