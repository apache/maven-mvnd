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
package org.mvndaemon.mvnd.common.logging;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.common.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerminalOutputTest {

    @Test
    void renderBarZero() {
        assertEquals("[                    ]", TerminalOutput.renderBar(0));
    }

    @Test
    void renderBarPartial() {
        // 30% -> filled = round(6.0) = 6 -> 5 '=' + '>' + 14 spaces
        assertEquals("[=====>              ]", TerminalOutput.renderBar(30));
    }

    @Test
    void renderBarFull() {
        assertEquals("[====================]", TerminalOutput.renderBar(100));
    }

    @Test
    void suffixAllPassing() {
        AttributedStringBuilder asb = new AttributedStringBuilder();
        TerminalOutput.appendTestProgress(
                asb, Message.projectTestProgress("app", 1, "com.acme.FooTest", "shouldWork", 12, 0, 0, 0));
        assertEquals(" [Tests: 12] FooTest#shouldWork", asb.toAttributedString().toString());
    }

    @Test
    void suffixFailuresRenderRed() {
        AttributedStringBuilder asb = new AttributedStringBuilder();
        TerminalOutput.appendTestProgress(
                asb, Message.projectTestProgress("app", 1, "com.acme.FooTest", "shouldWork", 12, 1, 0, 0));
        AttributedString s = asb.toAttributedString();
        assertEquals(" [Tests: 12, Failures: 1] FooTest#shouldWork", s.toString());
        int failureDigit = s.toString().indexOf("Failures: ") + "Failures: ".length();
        assertEquals(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED), s.styleAt(failureDigit));
        int bracket = s.toString().indexOf('[');
        assertEquals(AttributedStyle.DEFAULT.faint(), s.styleAt(bracket));
    }

    @Test
    void suffixErrorsAndSkips() {
        AttributedStringBuilder asb = new AttributedStringBuilder();
        TerminalOutput.appendTestProgress(
                asb, Message.projectTestProgress("app", 1, "com.acme.FooTest", "shouldWork", 5, 0, 2, 1));
        assertEquals(
                " [Tests: 5, Errors: 2, Skipped: 1] FooTest#shouldWork",
                asb.toAttributedString().toString());
    }

    @Test
    void suffixRetryingAndFlakyTests() {
        AttributedStringBuilder asb = new AttributedStringBuilder();
        TerminalOutput.appendTestProgress(
                asb,
                Message.projectTestProgress(
                        "app",
                        1,
                        "com.acme.FooTest",
                        "shouldWork",
                        4,
                        0,
                        0,
                        0,
                        1,
                        2,
                        java.util.List.of("FooTest#shouldWork", "FooTest#other"),
                        java.util.List.of(),
                        java.util.List.of()));
        AttributedString s = asb.toAttributedString();
        assertEquals(" [Tests: 4, Retrying: 1, Flaky: 2] FooTest#shouldWork", s.toString());
        int flakyDigit = s.toString().indexOf("Flaky: ") + "Flaky: ".length();
        assertEquals(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW), s.styleAt(flakyDigit));
    }

    @Test
    void suffixClassOnly() {
        AttributedStringBuilder asb = new AttributedStringBuilder();
        TerminalOutput.appendTestProgress(
                asb, Message.projectTestProgress("app", 1, "com.acme.FooTest", null, 3, 0, 0, 0));
        assertEquals(" [Tests: 3] FooTest", asb.toAttributedString().toString());
    }

    @Test
    void aggregateTestProgressSumsForkSnapshots() {
        Message.ProjectTestProgressEvent failed =
                Message.projectTestProgress("app", 1, "com.acme.FooTest", "failedTest", 3, 1, 0, 0);
        Message.ProjectTestProgressEvent skipped =
                Message.projectTestProgress("app", 2, "com.acme.FooTest", "skippedTest", 2, 0, 0, 1);

        Message.ProjectTestProgressEvent aggregated =
                TerminalOutput.aggregateTestProgress(java.util.List.of(failed, skipped));

        assertEquals(5, aggregated.getCompleted());
        assertEquals(1, aggregated.getFailures());
        assertEquals(0, aggregated.getErrors());
        assertEquals(1, aggregated.getSkipped());
        assertEquals("com.acme.FooTest", aggregated.getTestClass());
        assertEquals("skippedTest", aggregated.getTestMethod());
    }

    @Test
    void hidesProjectDetailsForLargeFailingReactors() {
        assertEquals(false, TerminalOutput.shouldShowProjectDetails(20, 10, 1));
        assertEquals(true, TerminalOutput.shouldShowProjectDetails(20, 10, 0));
        assertEquals(true, TerminalOutput.shouldShowProjectDetails(5, 10, 1));
    }

    @Test
    void stripDecorationRemovesLevelPrefixAndAnsi() {
        assertEquals("BUILD FAILURE", TerminalOutput.stripDecoration("[INFO] BUILD FAILURE"));
        assertEquals("BUILD FAILURE", TerminalOutput.stripDecoration("[INFO] [1mBUILD FAILURE[m"));
        assertEquals(
                "This project has been banned from the build due to previous failures.",
                TerminalOutput.stripDecoration(
                        "[INFO] This project has been banned from the build due to previous failures."));
    }

    @Test
    void bannedSkipFilterDropsBannedBlockButKeepsOtherLines() {
        TerminalOutput.BannedSkipFilter filter = new TerminalOutput.BannedSkipFilter();
        java.util.List<String> out = new java.util.ArrayList<>();
        String sep = "[INFO] ------------------------------------------------------------------------";
        String[] lines = {
            "[INFO] Reactor Summary:",
            "[INFO] ",
            sep,
            "[INFO] Skipping Camel :: YAML DSL",
            "[INFO] This project has been banned from the build due to previous failures.",
            sep,
            "[INFO] camel-core ......... SKIPPED",
        };
        for (String l : lines) {
            filter.accept(l, TerminalOutput.stripDecoration(l), out::add);
        }
        filter.flush(out::add);

        assertEquals(java.util.List.of("[INFO] Reactor Summary:", "[INFO] camel-core ......... SKIPPED"), out);
    }

    @Test
    void acceptReactorLineDropsBannedBlockWhenSuppressionEnabled() {
        String sep = "[INFO] ------------------------------------------------------------------------";
        String[] lines = {
            sep,
            "[INFO] Skipping Camel :: YAML DSL",
            "[INFO] This project has been banned from the build due to previous failures.",
            sep,
            "[INFO] Reactor Summary:",
            "[INFO] camel-core ......... SKIPPED",
        };

        java.util.List<String> out = new java.util.ArrayList<>();
        TerminalOutput.BannedSkipFilter filter = new TerminalOutput.BannedSkipFilter();
        for (String l : lines) {
            TerminalOutput.acceptReactorLine(l, true, filter, out::add);
        }
        filter.flush(out::add);

        assertEquals(java.util.List.of("[INFO] Reactor Summary:", "[INFO] camel-core ......... SKIPPED"), out);
    }

    @Test
    void reactorLineIsPassedThroughUnchangedWhenSuppressionDisabled() {
        java.util.List<String> out = new java.util.ArrayList<>();
        TerminalOutput.BannedSkipFilter filter = new TerminalOutput.BannedSkipFilter();
        TerminalOutput.acceptReactorLine(
                "[INFO] This project has been banned from the build due to previous failures.",
                false,
                filter,
                out::add);
        assertEquals(
                java.util.List.of("[INFO] This project has been banned from the build due to previous failures."), out);
    }

    @Test
    void bannedSkipFilterKeepsUnbannedSkippingLine() {
        TerminalOutput.BannedSkipFilter filter = new TerminalOutput.BannedSkipFilter();
        java.util.List<String> out = new java.util.ArrayList<>();
        filter.accept(
                "[INFO] Skipping bad plugin", TerminalOutput.stripDecoration("[INFO] Skipping bad plugin"), out::add);
        filter.accept("[INFO] Building foo", TerminalOutput.stripDecoration("[INFO] Building foo"), out::add);
        filter.flush(out::add);

        assertEquals(java.util.List.of("[INFO] Skipping bad plugin", "[INFO] Building foo"), out);
    }
}
