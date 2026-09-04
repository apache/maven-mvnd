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
package org.mvndaemon.mvnd.it;

import javax.inject.Inject;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.junit.MvndTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MvndTest(projectDir = "src/test/projects/test-progress-failure")
class TestProgressFailureTest {

    @Inject
    Client client;

    @Test
    void reportsFailedAndErroredTestsWithMessages() throws InterruptedException {
        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "clean", "test", "-B").assertFailure();

        List<Message.ProjectTestProgressEvent> events = output.getMessages().stream()
                .filter(Message.ProjectTestProgressEvent.class::isInstance)
                .map(Message.ProjectTestProgressEvent.class::cast)
                .toList();

        assertTrue(!events.isEmpty(), "expected PROJECT_TEST_PROGRESS messages, got none");
        assertTrue(
                events.stream()
                        .flatMap(e -> e.getFailedTests().stream())
                        .anyMatch(t -> t.startsWith("FailingServiceTest#failsAssertion") && t.contains(": ")),
                "expected the failed test to be reported with its message");
        assertTrue(
                events.stream()
                        .flatMap(e -> e.getErroredTests().stream())
                        .anyMatch(t -> t.startsWith("FailingServiceTest#throwsError") && t.contains(": ")),
                "expected the errored test to be reported with its message");

        List<String> logLines = output.getMessages().stream()
                .filter(Message.StringMessage.class::isInstance)
                .filter(m -> m.getType() == Message.BUILD_LOG_MESSAGE)
                .map(Message.StringMessage.class::cast)
                .map(Message.StringMessage::getMessage)
                .toList();

        int failuresLine = indexOfLineContaining(logLines, "Failures:");
        int errorsLine = indexOfLineContaining(logLines, "Errors:");
        // test-progress-failure is a single-module fixture, so Maven never prints a "Reactor Summary" section;
        // "BUILD FAILURE" is the banner that is always emitted, so it is the anchor used here instead.
        int buildFailureLine = indexOfLineContaining(logLines, "BUILD FAILURE");
        assertTrue(failuresLine >= 0, "expected a daemon-emitted log line containing 'Failures:', got: " + logLines);
        assertTrue(errorsLine >= 0, "expected a daemon-emitted log line containing 'Errors:', got: " + logLines);
        assertTrue(buildFailureLine >= 0, "expected a 'BUILD FAILURE' log line, got: " + logLines);
        assertTrue(failuresLine < buildFailureLine, "the test summary must be logged before the BUILD FAILURE banner");
        assertTrue(errorsLine < buildFailureLine, "the test summary must be logged before the BUILD FAILURE banner");
    }

    private static int indexOfLineContaining(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }
}
