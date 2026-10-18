// @layer: e2e
// @spec: 008-report-navigation
// @regression
package com.labflow.client.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labflow.client.support.ScenarioFiles;
import com.labflow.client.support.ScenarioFiles.Entry;
import com.labflow.common.config.LabConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The report of a real run, read from the result files Allure's Cucumber adapter wrote (spec 008,
 * the "Whole-feature pass" of technical-considerations section 4). It checks what a reader meets in
 * the report: the family / topic / sub-group tree, the short names, the descriptions, the steps
 * being the scenario's lines word for word with the requests nested under them, the run log
 * carrying the scenario's name, the names agreeing across the report, Surefire's XML and the
 * files, and nothing secret anywhere.
 *
 * <p><b>When it runs.</b> The results exist only after a run, and a run writes them while its
 * checks are still going, so this cannot be a check of the same run. It runs against the results
 * on disk, and is skipped, with the reason, unless they are those of one complete run (63 results,
 * 47 workflow and 16 screen). That makes it a post-run check: after the documented full run,
 * {@code ./mvnw -B -pl common,test-data,api-client test -Dtest=Spec008ReportContentAcceptanceTest
 * -Dsurefire.failIfNoSpecifiedTests=false} reads the report's data (the lab-free build alone finds no
 * results and skips it). The post-run command does not clean, so the results stay.
 */
class Spec008ReportContentAcceptanceTest {

    private static final Path RESULTS = Path.of("..", "target", "allure-results");
    private static final Path API_LOG = Path.of("..", "api-tests", "target", "logs", "api-tests.log");
    private static final Path SUREFIRE_API = Path.of("..", "api-tests", "target", "surefire-reports");
    private static final Path SUREFIRE_UI = Path.of("..", "ui-tests", "target", "surefire-reports");

    private static final Pattern REQUEST_STEP = Pattern.compile("^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS) /\\S* -> \\d{3} in \\d+ ms.*");
    private static final Pattern TOPIC = Pattern.compile("^(\\d{2})\\. \\S.*");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** One result file, read. */
    private record Result(String file, JsonNode json) {
        String name() {
            return json.path("name").asText();
        }

        List<String> label(String label) {
            List<String> values = new ArrayList<>();
            for (JsonNode node : json.path("labels")) {
                if (node.path("name").asText().equals(label)) {
                    values.add(node.path("value").asText());
                }
            }
            return values;
        }

        String only(String label) {
            List<String> values = label(label);
            assertThat(values).as(file + ": label '" + label + "' appears exactly once").hasSize(1);
            return values.get(0);
        }
    }

    private static List<Result> results = List.of();

    @BeforeAll
    static void readTheResultsOfAFullRun() throws IOException {
        assumeTrue(Files.isDirectory(RESULTS), "no " + RESULTS + ": run the full suite first (this check reads the report data of a run)");
        List<Result> read = new ArrayList<>();
        try (Stream<Path> files = Files.list(RESULTS)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith("-result.json")).sorted().toList()) {
                read.add(new Result(file.getFileName().toString(), JSON.readTree(file.toFile())));
            }
        }
        // Only the total is an assumption: a wrong family name must fail the checks below, not make them skip.
        assumeTrue(read.size() == 63, "the results on disk are not those of one complete run (" + read.size()
                + " results, 63 expected); run the whole suite without a label filter");
        results = read;
    }

    @Test
    @DisplayName("The grouped view is a tree of two families, numbered topics and Accepted / Refused sub-groups, and the Suites view mirrors it in plain language")
    void theReportIsAFamilyTopicSubGroupTree() {
        for (Result result : results) {
            String family = result.only("epic");
            String topic = result.only("feature");
            String subGroup = result.only("story");

            assertThat(family).as(result.name()).isIn("API tests", "UI tests");
            assertThat(topic).as(result.name() + ": topic carries its two-digit number").matches(TOPIC);
            assertThat(subGroup).as(result.name()).isIn("Accepted", "Refused");
            assertThat(result.only("parentSuite")).as(result.name() + ": Suites view, level 1 = family").isEqualTo(family);
            assertThat(result.only("suite")).as(result.name() + ": Suites view, level 2 = topic").isEqualTo(topic);
            assertThat(result.only("subSuite")).as(result.name() + ": Suites view, level 3 = sub-group").isEqualTo(subGroup);
            assertThat(result.label("suite")).as(result.name() + ": no suite named after a place in the code").noneMatch(s -> s.endsWith("Test") || s.contains("com.labflow"));
        }

        Map<String, Long> perFamily = new TreeMap<>();
        results.forEach(r -> perFamily.merge(r.only("epic"), 1L, Long::sum));
        assertThat(perFamily).as("entries per family").containsExactly(Map.entry("API tests", 47L), Map.entry("UI tests", 16L));

        Map<String, TreeSet<String>> topicsByFamily = new TreeMap<>();
        results.forEach(r -> topicsByFamily.computeIfAbsent(r.only("epic"), k -> new TreeSet<>()).add(r.only("feature")));
        Map<String, String> nameForNumber = new HashMap<>();
        topicsByFamily.values().forEach(topics -> topics.forEach(topic ->
                assertThat(nameForNumber.computeIfAbsent(topic.substring(0, 2), n -> topic)).as("topic " + topic.substring(0, 2) + " has one name in both families").isEqualTo(topic)));
        assertThat(topicsByFamily.get("API tests")).as("workflow topics").hasSize(9);
        assertThat(topicsByFamily.get("UI tests")).as("screen topics").hasSize(6);
    }

    @Test
    @DisplayName("Sorted by name as the report sorts, topics come in workflow order with Accepted before Refused, and no sub-group lists more than 12 entries or repeats a name")
    void theReportListsTopicsAndChecksInAFixedOrder() {
        for (String family : List.of("API tests", "UI tests")) {
            List<Result> inFamily = results.stream().filter(r -> r.only("epic").equals(family)).toList();
            List<String> topics = inFamily.stream().map(r -> r.only("feature")).distinct()
                    .sorted(Comparator.comparing((String t) -> t.toLowerCase(Locale.ROOT))).toList();
            List<Integer> numbers = topics.stream().map(t -> Integer.parseInt(t.substring(0, 2))).toList();

            assertThat(numbers).as(family + ": topics as the report sorts them, by name, are in workflow order")
                    .isEqualTo(family.startsWith("API") ? List.of(1, 2, 3, 4, 5, 6, 7, 8, 9) : List.of(1, 4, 5, 6, 7, 9));

            for (String topic : topics) {
                List<Result> inTopic = inFamily.stream().filter(r -> r.only("feature").equals(topic)).toList();
                List<String> subGroups = inTopic.stream().map(r -> r.only("story")).distinct()
                        .sorted(Comparator.comparing((String s) -> s.toLowerCase(Locale.ROOT))).toList();
                assertThat(subGroups).as(family + " / " + topic + ": sub-groups as sorted, empty ones absent")
                        .isIn(List.of("Accepted"), List.of("Refused"), List.of("Accepted", "Refused"));
                for (String subGroup : subGroups) {
                    List<String> names = inTopic.stream().filter(r -> r.only("story").equals(subGroup)).map(Result::name).toList();
                    assertThat(names).as(family + " / " + topic + " / " + subGroup).hasSizeLessThanOrEqualTo(12).doesNotHaveDuplicates();
                }
            }
        }
        assertThat(results.stream().map(Result::name)).as("every check's name is at most 45 characters").allSatisfy(name -> assertThat(name.length()).isLessThanOrEqualTo(45));
    }

    @Test
    @DisplayName("Every check in the report is a scenario of a scenario file, one for one: none missing, none twice, each with its fuller description")
    void everyScenarioIsInTheReportExactlyOnceWithItsDescription() {
        List<Entry> written = Stream.concat(
                ScenarioFiles.in(ScenarioFiles.WORKFLOW, "API tests").stream(),
                ScenarioFiles.in(ScenarioFiles.SCREEN, "UI tests").stream()).toList();

        List<String> inReport = results.stream().map(r -> r.only("epic") + " / " + r.only("feature") + " / " + r.name()).sorted().toList();
        List<String> inFiles = written.stream().map(e -> e.family() + " / " + e.topic() + " / " + e.name()).sorted().toList();
        assertThat(inReport).as("report entries against scenario entries (outline rows one by one)").isEqualTo(inFiles);

        Map<String, Entry> byKey = written.stream().collect(Collectors.toMap(e -> e.family() + " / " + e.topic() + " / " + e.name(), e -> e));
        for (Result result : results) {
            Entry entry = byKey.get(result.only("epic") + " / " + result.only("feature") + " / " + result.name());
            String description = squash(result.json().path("description").asText());
            assertThat(description).as(result.name() + ": a description beside the short name").isNotBlank();
            assertThat(description).as(result.name() + ": the scenario's own description is shown").contains(squash(entry.description()));
            assertThat(result.only("subSuite")).as(result.name() + ": sub-group follows the tag").isEqualTo(entry.subGroup());
        }
    }

    @Test
    @DisplayName("A description is shown as paragraphs: no line break sits between two words, only blank lines between paragraphs")
    void descriptionsAreParagraphsNotBrokenLines() {
        for (Result result : results) {
            String description = result.json().path("description").asText();
            assertThat(description).as(result.name() + ": a description is there").isNotBlank();
            assertThat(description).as(result.name() + ": no single line break inside a paragraph").doesNotContainPattern("[^\\n]\\n[^\\n]");
            assertThat(result.json().path("descriptionHtml").asText()).as(result.name() + ": shown as one block per paragraph").startsWith("<p>").endsWith("</p>");
            assertThat(description).as(result.name() + ": no indentation carried over from the file").doesNotStartWith(" ").doesNotContain("\n ");
        }
    }

    @Test
    @DisplayName("An opened check's steps are the lines of its scenario, word for word and in the same order")
    void theStepsAreTheScenarioLinesWordForWord() {
        Map<String, Entry> byKey = Stream.concat(
                ScenarioFiles.in(ScenarioFiles.WORKFLOW, "API tests").stream(),
                ScenarioFiles.in(ScenarioFiles.SCREEN, "UI tests").stream())
                .collect(Collectors.toMap(e -> e.family() + " / " + e.topic() + " / " + e.name(), e -> e));

        List<String> offences = new ArrayList<>();
        for (Result result : results) {
            Entry entry = byKey.get(result.only("epic") + " / " + result.only("feature") + " / " + result.name());
            assertThat(entry).as(result.name() + " is a scenario of a scenario file").isNotNull();
            List<String> steps = new ArrayList<>();
            result.json().path("steps").forEach(step -> steps.add(step.path("name").asText().strip()));
            if (!steps.equals(entry.lines())) {
                offences.add(result.name() + "\n   report: " + steps + "\n   file:   " + entry.lines());
            }
        }

        assertThat(offences).as("checks whose steps differ from their scenario's lines").isEmpty();
    }

    @Test
    @DisplayName("A workflow check shows each request to the lab under the scenario line that made it, in order, with kind, path, status and time")
    void requestsAreNestedUnderTheLineThatMadeThem() {
        List<Result> workflow = results.stream().filter(r -> r.only("epic").equals("API tests")).toList();
        int withRequests = 0;
        for (Result result : workflow) {
            long previousStart = Long.MIN_VALUE;
            int requests = 0;
            for (JsonNode step : result.json().path("steps")) {
                long stepStart = step.path("start").asLong();
                assertThat(stepStart).as(result.name() + ": lines in order").isGreaterThanOrEqualTo(previousStart);
                previousStart = stepStart;
                long previousRequest = stepStart;
                for (JsonNode request : step.path("steps")) {
                    requests++;
                    assertThat(request.path("name").asText()).as(result.name() + ": request line under '" + step.path("name").asText() + "'").matches(REQUEST_STEP);
                    assertThat(request.path("start").asLong()).as(result.name() + ": requests in order").isGreaterThanOrEqualTo(previousRequest);
                    previousRequest = request.path("start").asLong();
                }
            }
            if (requests > 0) {
                withRequests++;
            }
        }

        assertThat(withRequests).as("workflow checks that show requests (every check talks to the lab)").isEqualTo(workflow.size());
        assertThat(results.stream().filter(r -> r.only("epic").equals("UI tests")).flatMap(r -> stream(r.json().path("steps")))
                .flatMap(step -> stream(step.path("steps"))).map(step -> step.path("name").asText()))
                .as("a screen check shows no request lines").noneMatch(name -> REQUEST_STEP.matcher(name).matches());
    }

    @Test
    @DisplayName("Every workflow check's request lines in the run log carry its topic and short name, so a line can be found by the name the report shows")
    void theRunLogLinesCarryTheScenarioName() throws IOException {
        assumeTrue(Files.isRegularFile(API_LOG), "no run log at " + API_LOG);
        String log = Files.readString(API_LOG, StandardCharsets.UTF_8);

        List<String> notInLog = results.stream().filter(r -> r.only("epic").equals("API tests"))
                .filter(r -> !Pattern.compile(Pattern.quote(r.only("feature") + " › " + r.name()) + " (GET|POST|PUT|PATCH|DELETE) /").matcher(log).find())
                .map(Result::name).toList();

        assertThat(notInLog).as("workflow checks with no request line under their name in the run log").isEmpty();
    }

    @Test
    @DisplayName("The name Surefire, the console and the run-page summary use is the report's short name: each scenario's test case carries exactly it")
    void surefireReportsEachScenarioUnderItsShortName() throws Exception {
        for (Object[] family : new Object[][] {{"API tests", SUREFIRE_API}, {"UI tests", SUREFIRE_UI}}) {
            Path reports = (Path) family[1];
            assumeTrue(Files.isDirectory(reports), "no Surefire reports at " + reports);
            List<String> surefire = new ArrayList<>();
            try (Stream<Path> files = Files.list(reports)) {
                for (Path xml : files.filter(p -> p.getFileName().toString().matches("TEST-.*ScenariosTest\\.xml")).toList()) {
                    NodeList cases = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toFile()).getElementsByTagName("testcase");
                    for (int i = 0; i < cases.getLength(); i++) {
                        surefire.add(((Element) cases.item(i)).getAttribute("name"));
                    }
                }
            }
            List<String> report = results.stream().filter(r -> r.only("epic").equals(family[0])).map(Result::name).sorted().toList();

            assertThat(surefire.stream().sorted().toList()).as(family[0] + ": test cases in Surefire's XML against the report's names").isEqualTo(report);
        }
    }

    @Test
    @DisplayName("No check carries example values as parameters, so the report shows nothing in grey beside a name")
    void noCheckShowsItsExampleValuesBesideItsName() {
        List<String> offences = results.stream()
                .filter(r -> r.json().path("parameters").size() > 0)
                .map(r -> r.name() + " has " + r.json().path("parameters").size() + " parameter(s)")
                .toList();

        assertThat(offences).as("checks that list parameters (example values) beside their name").isEmpty();
    }

    @Test
    @DisplayName("Every check has a test case id and a history id of its own, so the report folds none into another as a retry and lists all of them")
    void noCheckSharesItsIdsWithAnother() {
        for (String id : List.of("testCaseId", "historyId")) {
            Map<String, List<String>> byId = new TreeMap<>();
            results.forEach(r -> byId.computeIfAbsent(r.json().path(id).asText(), k -> new ArrayList<>()).add(r.only("epic") + " / " + r.name()));

            assertThat(byId.values().stream().filter(names -> names.size() > 1).toList())
                    .as("checks that share a " + id + " (the report would list only one of them)").isEmpty();
            assertThat(byId).as("one " + id + " per check").hasSize(results.size());
        }
    }

    @Test
    @DisplayName("Every set-up and tear-down entry in the report has a plain name, none taken from the code")
    void setUpAndTearDownEntriesHavePlainNames() throws IOException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(RESULTS)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith("-container.json")).toList()) {
                JsonNode container = JSON.readTree(file.toFile());
                for (String kind : List.of("befores", "afters")) {
                    container.path(kind).forEach(fixture -> names.add(fixture.path("name").asText()));
                }
            }
        }

        assertThat(names).as("set-up and tear-down entries recorded by the run").isNotEmpty();
        assertThat(names).as("entries named after a hook method in the code").noneMatch(name -> name.contains("com.labflow") || name.contains("(") || name.contains("io.cucumber"));
    }

    @Test
    @DisplayName("Searching the report's data for the access key or the sign-in password finds nothing")
    void noSecretIsInTheReportData() throws IOException {
        List<String> secrets = List.of(LabConfig.fromEnvironment().apiKey().reveal(), LabConfig.DEFAULT_API_KEY, "I_Like_" + "This_Lab");
        List<String> offences = new ArrayList<>();
        try (Stream<Path> files = Files.list(RESULTS)) {
            for (Path file : files.filter(p -> p.getFileName().toString().matches(".*\\.(json|txt|properties|csv)")).toList()) {
                String text;
                try {
                    text = Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    continue;
                }
                for (int i = 0; i < secrets.size(); i++) {
                    if (text.contains(secrets.get(i))) {
                        offences.add(file.getFileName() + " holds " + (i < 2 ? "the access key" : "the password") + " (value not shown)");
                    }
                }
            }
        }

        assertThat(offences).as("secrets in the report data").isEmpty();
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    private static Stream<JsonNode> stream(JsonNode array) {
        List<JsonNode> nodes = new ArrayList<>();
        array.forEach(nodes::add);
        return nodes.stream();
    }
}
