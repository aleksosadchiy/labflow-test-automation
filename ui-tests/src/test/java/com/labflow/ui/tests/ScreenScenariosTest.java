package com.labflow.ui.tests;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * The entry point of the screen scenarios for Maven. Surefire only reports tests that belong to
 * a class, and a Gherkin scenario has none, so a scenario's verdict (and an undefined step) would
 * never reach the build. Surefire does find this class, and the suite engine runs the scenarios
 * from the {@code features} folder through the Cucumber engine inside it, so every scenario is
 * reported as a test of this class.
 *
 * <p>Settings (glue, parallel strategy, order, naming, report plugin) stay in
 * {@code junit-platform.properties}. The class has no code of its own.
 */
@Suite(failIfNoTests = false)
@IncludeEngines("cucumber")
@SelectPackages("features")
public class ScreenScenariosTest {
}
