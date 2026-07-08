/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.mvndaemon.mvnd.logging.smart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects the reactor-wide failed/errored/flaky test identities and numeric totals needed to render the
 * end-of-build test summary. Records arrive on fork-reader threads via {@link #record}, are folded into the
 * running totals on build threads via {@link #foldProject}, and are rendered on the main thread via
 * {@link #renderLines()}; all three are synchronized so the object can be shared across those threads.
 */
public class TestBuildSummary {

    /** Test-summary line severity, mirroring Maven's INFO/WARNING/ERROR levels. */
    public enum SummaryLevel {
        INFO,
        WARNING,
        ERROR
    }

    /** One line of the rendered summary, tagged with the level it should be logged at. */
    public static final class SummaryLine {
        public final SummaryLevel level;
        public final String text;

        SummaryLine(SummaryLevel level, String text) {
            this.level = level;
            this.text = text;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SummaryLine)) {
                return false;
            }
            SummaryLine other = (SummaryLine) o;
            return level == other.level && text.equals(other.text);
        }

        @Override
        public int hashCode() {
            return 31 * level.hashCode() + text.hashCode();
        }

        @Override
        public String toString() {
            return "[" + level + "] " + text;
        }
    }

    private final Map<String, Set<String>> failedTests = new LinkedHashMap<>();
    private final Map<String, Set<String>> erroredTests = new LinkedHashMap<>();
    private final Map<String, Set<String>> flakyTests = new LinkedHashMap<>();
    /** Latest cumulative per-fork snapshot for each project, indexed by fork channel id. */
    private final Map<String, Map<Integer, int[]>> currentByProject = new LinkedHashMap<>();

    private TestTotals totals = TestTotals.EMPTY;

    /**
     * Records one project/fork's latest cumulative snapshot and unions in any newly reported test identities.
     * Union is collision-free, so fork-channel-id reuse across surefire/failsafe does not affect the identity sets.
     */
    public synchronized void record(
            String projectId,
            int forkChannelId,
            int completed,
            int failures,
            int errors,
            int skipped,
            int retrying,
            int flaky,
            List<String> flakyTests,
            List<String> failedTests,
            List<String> erroredTests) {
        if (!flakyTests.isEmpty()) {
            this.flakyTests
                    .computeIfAbsent(projectId, k -> new LinkedHashSet<>())
                    .addAll(flakyTests);
        }
        if (!failedTests.isEmpty()) {
            this.failedTests
                    .computeIfAbsent(projectId, k -> new LinkedHashSet<>())
                    .addAll(failedTests);
        }
        if (!erroredTests.isEmpty()) {
            this.erroredTests
                    .computeIfAbsent(projectId, k -> new LinkedHashSet<>())
                    .addAll(erroredTests);
        }
        currentByProject
                .computeIfAbsent(projectId, k -> new LinkedHashMap<>())
                .put(forkChannelId, new int[] {completed, failures, errors, skipped, retrying, flaky});
    }

    /**
     * Sums the given project's latest per-fork snapshots into the running reactor-wide totals, then clears them.
     * Called at each mojo boundary so cumulative-per-fork counts are summed correctly across surefire+failsafe
     * (fork ids restart per plugin execution). A no-op if the project reported no test progress.
     */
    public synchronized void foldProject(String projectId) {
        Map<Integer, int[]> snapshots = currentByProject.remove(projectId);
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        int completed = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        int flaky = 0;
        for (int[] snapshot : snapshots.values()) {
            completed += snapshot[0];
            failures += snapshot[1];
            errors += snapshot[2];
            skipped += snapshot[3];
            flaky += snapshot[5];
        }
        totals = new TestTotals(
                totals.completed + completed,
                totals.failures + failures,
                totals.errors + errors,
                totals.skipped + skipped,
                totals.flaky + flaky);
    }

    /**
     * Folds in any projects whose snapshots have not yet been folded, then renders the reactor-wide failed/errored/
     * flaky test summary in the same shape Surefire itself uses (Results: / Failures: / Errors: / Flakes: /
     * Tests run: ...). Returns an empty list when there is nothing to report.
     */
    public synchronized List<SummaryLine> renderLines() {
        for (String projectId : new ArrayList<>(currentByProject.keySet())) {
            foldProject(projectId);
        }
        List<SummaryLine> out = new ArrayList<>();
        if (failedTests.isEmpty() && erroredTests.isEmpty() && flakyTests.isEmpty()) {
            return out;
        }
        emit(out, SummaryLevel.INFO, "");
        emit(out, SummaryLevel.INFO, "Results:");
        emit(out, SummaryLevel.INFO, "");
        emitFailureCategory(out, "Failures: ", failedTests);
        emitFailureCategory(out, "Errors: ", erroredTests);
        emitFlakyCategory(out, "Flakes: ", flakyTests);
        emit(out, SummaryLevel.INFO, "");
        emit(out, trailerLevel(totals), trailerLine(totals));
        emit(out, SummaryLevel.INFO, "");
        return out;
    }

    private static void emit(List<SummaryLine> out, SummaryLevel level, String text) {
        out.add(new SummaryLine(level, text));
    }

    /** Renders a "Failures: "/"Errors: " section: header plus one {@code "  <project> <test>"} line per entry. */
    private static void emitFailureCategory(List<SummaryLine> out, String header, Map<String, Set<String>> byProject) {
        if (byProject.isEmpty()) {
            return;
        }
        emit(out, SummaryLevel.ERROR, header);
        for (Map.Entry<String, Set<String>> entry : byProject.entrySet()) {
            for (String test : entry.getValue()) {
                emit(out, SummaryLevel.ERROR, "  " + entry.getKey() + " " + test);
            }
        }
    }

    /**
     * Renders the "Flakes: " section. Each entry is a {@code TestProgressAccumulator}-formatted multi-line block
     * (display name, then one {@code "  Run N: PASS"}/{@code "  Run N: <message>"} line per attempt); this splits
     * that block and re-levels each line: the header/test-name lines are WARNING, a passing run is INFO, a failing
     * run is ERROR -- matching Surefire's own per-line coloring exactly.
     */
    private static void emitFlakyCategory(List<SummaryLine> out, String header, Map<String, Set<String>> byProject) {
        if (byProject.isEmpty()) {
            return;
        }
        emit(out, SummaryLevel.WARNING, header);
        for (Map.Entry<String, Set<String>> entry : byProject.entrySet()) {
            for (String detail : entry.getValue()) {
                String[] lines = detail.split("\n", -1);
                emit(out, SummaryLevel.WARNING, "  " + entry.getKey() + " " + lines[0]);
                for (int i = 1; i < lines.length; i++) {
                    String runLine = lines[i];
                    boolean passed = runLine.trim().endsWith(": PASS");
                    emit(out, passed ? SummaryLevel.INFO : SummaryLevel.ERROR, "  " + runLine);
                }
            }
        }
    }

    private static SummaryLevel trailerLevel(TestTotals totals) {
        if (totals.failures > 0 || totals.errors > 0) {
            return SummaryLevel.ERROR;
        }
        return totals.flaky > 0 ? SummaryLevel.WARNING : SummaryLevel.INFO;
    }

    private static String trailerLine(TestTotals totals) {
        StringBuilder sb = new StringBuilder("Tests run: ")
                .append(totals.completed)
                .append(", Failures: ")
                .append(totals.failures)
                .append(", Errors: ")
                .append(totals.errors)
                .append(", Skipped: ")
                .append(totals.skipped);
        if (totals.flaky > 0) {
            sb.append(", Flakes: ").append(totals.flaky);
        }
        return sb.toString();
    }

    /** Reactor-wide test totals, folded in as each project's test-running mojo execution finishes. */
    static final class TestTotals {
        static final TestTotals EMPTY = new TestTotals(0, 0, 0, 0, 0);

        final int completed;
        final int failures;
        final int errors;
        final int skipped;
        final int flaky;

        TestTotals(int completed, int failures, int errors, int skipped, int flaky) {
            this.completed = completed;
            this.failures = failures;
            this.errors = errors;
            this.skipped = skipped;
            this.flaky = flaky;
        }
    }
}
