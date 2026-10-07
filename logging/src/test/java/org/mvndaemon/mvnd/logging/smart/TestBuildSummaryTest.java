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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.logging.smart.TestBuildSummary.SummaryLevel;
import org.mvndaemon.mvnd.logging.smart.TestBuildSummary.SummaryLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBuildSummaryTest {

    @Test
    void rendersSurefireStyleBlockWithTagsAndTrailer() {
        TestBuildSummary summary = new TestBuildSummary();
        summary.record(
                "camel-jms",
                1,
                40,
                1,
                0,
                0,
                0,
                0,
                List.of(),
                List.of("FooTest#bar: expected <5> but was <4>"),
                List.of());
        summary.foldProject("camel-jms");
        summary.record("camel-nats", 1, 1, 0, 1, 0, 0, 0, List.of(), List.of(), List.of("NatsIT#connects: refused"));
        summary.foldProject("camel-nats");
        summary.record(
                "camel-mllp",
                1,
                1,
                0,
                0,
                0,
                0,
                1,
                List.of("FlakyTest#retries\n  Run 1: boom\n  Run 2: PASS"),
                List.of(),
                List.of());
        summary.foldProject("camel-mllp");

        List<SummaryLine> lines = summary.renderLines();

        assertEquals(
                List.of(
                        line(SummaryLevel.INFO, ""),
                        line(SummaryLevel.INFO, "Results:"),
                        line(SummaryLevel.INFO, ""),
                        line(SummaryLevel.ERROR, "Failures: "),
                        line(SummaryLevel.ERROR, "  camel-jms FooTest#bar: expected <5> but was <4>"),
                        line(SummaryLevel.ERROR, "Errors: "),
                        line(SummaryLevel.ERROR, "  camel-nats NatsIT#connects: refused"),
                        line(SummaryLevel.WARNING, "Flakes: "),
                        line(SummaryLevel.WARNING, "  camel-mllp FlakyTest#retries"),
                        line(SummaryLevel.ERROR, "    Run 1: boom"),
                        line(SummaryLevel.INFO, "    Run 2: PASS"),
                        line(SummaryLevel.INFO, ""),
                        line(SummaryLevel.ERROR, "Tests run: 42, Failures: 1, Errors: 1, Skipped: 0, Flakes: 1"),
                        line(SummaryLevel.INFO, "")),
                lines);
    }

    @Test
    void omitsFlakesSuffixWhenNoFlakyTests() {
        TestBuildSummary summary = new TestBuildSummary();
        summary.record(
                "camel-jms",
                1,
                10,
                1,
                0,
                0,
                0,
                0,
                List.of(),
                List.of("FooTest#bar: expected <5> but was <4>"),
                List.of());
        summary.foldProject("camel-jms");

        List<SummaryLine> lines = summary.renderLines();

        SummaryLine trailer = lines.get(lines.size() - 2);
        assertEquals(SummaryLevel.ERROR, trailer.level);
        assertEquals("Tests run: 10, Failures: 1, Errors: 0, Skipped: 0", trailer.text);
    }

    @Test
    void emitsNothingWhenAllCategoriesEmpty() {
        TestBuildSummary summary = new TestBuildSummary();
        summary.record("camel-core", 1, 5, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
        summary.foldProject("camel-core");

        assertEquals(List.of(), summary.renderLines());
    }

    @Test
    void unionsFailedTestIdentitiesAcrossForksWithinTheSameProject() {
        TestBuildSummary summary = new TestBuildSummary();
        summary.record("camel-jms", 1, 1, 1, 0, 0, 0, 0, List.of(), List.of("FooTest#bar: boom"), List.of());
        // A second fork on the same project reports a different failed test; identities must union, not overwrite.
        summary.record("camel-jms", 2, 1, 1, 0, 0, 0, 0, List.of(), List.of("BarTest#baz: boom"), List.of());
        // Re-recording the same test on the same fork must not create a duplicate line (Set semantics).
        summary.record("camel-jms", 1, 2, 1, 0, 0, 0, 0, List.of(), List.of("FooTest#bar: boom"), List.of());
        summary.foldProject("camel-jms");

        List<SummaryLine> lines = summary.renderLines();
        List<String> failureLines = lines.stream()
                .filter(l -> l.text.startsWith("  camel-jms"))
                .map(l -> l.text)
                .toList();
        assertEquals(List.of("  camel-jms FooTest#bar: boom", "  camel-jms BarTest#baz: boom"), failureLines);
    }

    @Test
    void foldProjectSumsCumulativeCountsAcrossSurefireThenFailsafeReusingForkChannelIds() {
        TestBuildSummary summary = new TestBuildSummary();
        // Surefire execution: fork channel 1 finishes with 5 completed tests.
        summary.record("app", 1, 5, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
        summary.foldProject("app");
        // Failsafe execution reuses fork channel id 1 with its own cumulative count; must add, not replace.
        summary.record("app", 1, 3, 1, 0, 0, 0, 0, List.of(), List.of("ItTest#works: boom"), List.of());
        summary.foldProject("app");

        List<SummaryLine> lines = summary.renderLines();
        SummaryLine trailer = lines.get(lines.size() - 2);
        assertEquals("Tests run: 8, Failures: 1, Errors: 0, Skipped: 0", trailer.text);
    }

    @Test
    void renderLinesFoldsAnyProjectsNotYetFolded() {
        TestBuildSummary summary = new TestBuildSummary();
        summary.record("app", 1, 4, 1, 0, 0, 0, 0, List.of(), List.of("FooTest#bar: boom"), List.of());
        // No explicit foldProject call: renderLines() must fold remaining snapshots itself.

        List<SummaryLine> lines = summary.renderLines();

        assertTrue(lines.stream().anyMatch(l -> l.text.equals("Tests run: 4, Failures: 1, Errors: 0, Skipped: 0")));
    }

    private static SummaryLine line(SummaryLevel level, String text) {
        return new SummaryLine(level, text);
    }
}
