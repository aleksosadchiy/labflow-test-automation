package com.labflow.ui.tests.world;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.labflow.client.LabClient;
import com.labflow.common.config.LabConfig;
import com.labflow.common.model.Patient;
import com.labflow.common.model.SampleType;
import com.labflow.common.model.WorkflowStage;
import com.labflow.testdata.Patients;
import com.labflow.ui.config.UiCredentials;
import com.labflow.ui.pages.CreateSamplePage;
import com.labflow.ui.pages.LoginPage;
import com.labflow.ui.pages.ReportPage;
import com.labflow.ui.pages.SampleDetailsPage;
import com.labflow.ui.pages.SampleListPage;

/**
 * Everything one screen scenario knows: the page its hooks opened, the lab's settings, and the
 * screen it is on. It holds the lab settings, the sign-in
 * and the data helper that the former Jupiter base class held.
 *
 * <p>Cucumber's PicoContainer creates one instance per scenario and hands the same instance to
 * every step and hook class of that scenario through its constructor, so two scenarios never
 * share state, even when they run side by side.
 */
public class UiWorld {

    private final LabConfig config = LabConfig.fromEnvironment();
    private final UiCredentials credentials = UiCredentials.fromEnvironment();
    private final LabClient lab = LabClient.create(config);

    private BrowserContext browserContext;
    private Page page;
    private LoginPage loginPage;
    private SampleListPage sampleListPage;
    private CreateSamplePage createSamplePage;
    private SampleDetailsPage sampleDetailsPage;
    private ReportPage reportPage;
    private Patient patient;
    private String sampleId;

    public LabConfig config() {
        return config;
    }

    public UiCredentials credentials() {
        return credentials;
    }

    /** Called by the hooks once the scenario has its own context and page. */
    public void browserIs(BrowserContext context, Page freshPage) {
        this.browserContext = context;
        this.page = freshPage;
    }

    /** The context of this scenario, or null before the hooks opened one. */
    public BrowserContext browserContext() {
        return browserContext;
    }

    /** The scenario's own page, a fresh one for every scenario. */
    public Page page() {
        return require(page, "a page opened by the hooks");
    }

    /** Signs in with the lab's configured credentials and lands on the Sample List screen. */
    public SampleListPage signedIn() {
        return LoginPage.open(page(), config).signIn(credentials);
    }

    /** Registers a new, uniquely named patient through the lab's connected interface. */
    public Patient newPatient() {
        return lab.patients().register(Patients.valid().name());
    }

    /**
     * Registers a new sample for a new patient through the lab's connected interface and moves it
     * on, one stage at a time, until it is at the given stage. The lab has no screen for this
     * set-up, so the checks prepare it this way before the screen steps begin.
     */
    public void sampleHeldAt(WorkflowStage stage) {
        this.patient = newPatient();
        this.sampleId = lab.samples().register(patient.id(), SampleType.BLOOD).id();
        if (stage != WorkflowStage.ACCESSIONED) {
            lab.samples().walkTo(sampleId, stage);
        }
    }

    /** The id of the sample the lab holds for this scenario. */
    public String sampleId() {
        return require(sampleId, "a sample held by the lab");
    }

    public void signInScreenIs(LoginPage screen) {
        this.loginPage = screen;
    }

    /** The Sign In screen the scenario is on. */
    public LoginPage signInScreen() {
        return require(loginPage, "the Sign In screen opened in an earlier step");
    }

    public void sampleListIs(SampleListPage screen) {
        this.sampleListPage = screen;
    }

    /** The Sample List screen the scenario landed on. */
    public SampleListPage sampleList() {
        return require(sampleListPage, "the Sample List screen reached in an earlier step");
    }

    public void createSampleScreenIs(CreateSamplePage screen) {
        this.createSamplePage = screen;
    }

    /** The Create Sample screen the scenario is on. */
    public CreateSamplePage createSampleScreen() {
        return require(createSamplePage, "the Create Sample screen opened in an earlier step");
    }

    public void sampleDetailsIs(SampleDetailsPage screen) {
        this.sampleDetailsPage = screen;
    }

    /** The Details screen of the sample the scenario registered. */
    public SampleDetailsPage sampleDetails() {
        return require(sampleDetailsPage, "the Details screen reached by registering a sample");
    }

    public void reportScreenIs(ReportPage screen) {
        this.reportPage = screen;
    }

    /** The Report screen the scenario opened. */
    public ReportPage reportScreen() {
        return require(reportPage, "the Report screen opened in an earlier step");
    }

    public void patientIs(Patient registered) {
        this.patient = registered;
    }

    /** The patient the scenario registered through the lab's connected interface. */
    public Patient patient() {
        return require(patient, "a registered patient");
    }

    private static <T> T require(T value, String what) {
        if (value == null) {
            throw new IllegalStateException("This step needs " + what + " before it");
        }
        return value;
    }
}
