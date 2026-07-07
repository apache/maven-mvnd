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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MvndTest(projectDir = "src/test/projects/test-progress")
class TestProgressTest {

    @Inject
    Client client;

    @Test
    void emitsIncreasingTestProgress() throws InterruptedException {
        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "clean", "test", "-B").assertSuccess();

        List<Message.ProjectTestProgressEvent> events = testProgressEvents(output);

        assertTrue(!events.isEmpty(), "expected PROJECT_TEST_PROGRESS messages, got none");
        int maxCompleted = events.stream()
                .mapToInt(Message.ProjectTestProgressEvent::getCompleted)
                .max()
                .orElse(0);
        assertTrue(
                maxCompleted >= 3,
                "expected completed count to reach the number of executed tests, got " + maxCompleted);
        assertTrue(
                events.stream().anyMatch(e -> "org.mvndaemon.mvnd.test.MyServiceTest".equals(e.getTestClass())),
                "expected the current test class name to be reported");
    }

    @Test
    void emitsFlakyTestProgress() throws InterruptedException {
        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "clean", "test", "-B").assertSuccess();

        List<Message.ProjectTestProgressEvent> events = testProgressEvents(output);

        assertTrue(
                events.stream().anyMatch(e -> e.getRetrying() > 0),
                "expected a retrying snapshot while the flaky test was being rerun");
        assertTrue(
                events.stream().anyMatch(e -> e.getFlaky() > 0), "expected a flaky snapshot after the rerun succeeded");
        assertTrue(
                events.stream().anyMatch(e -> e.getFlakyTests().contains("FlakyServiceTest#succeedsOnRetry")),
                "expected the recovered test to be reported in the flaky test list");
    }

    @Test
    void disabledEmitsNoTestProgress() throws InterruptedException {
        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "clean", "test", "-B", "-Dmvnd.testProgress=false")
                .assertSuccess();

        assertEquals(0, testProgressEvents(output).size(), "no test-progress messages expected when feature disabled");
    }

    private static List<Message.ProjectTestProgressEvent> testProgressEvents(TestClientOutput output) {
        return output.getMessages().stream()
                .filter(Message.ProjectTestProgressEvent.class::isInstance)
                .map(Message.ProjectTestProgressEvent.class::cast)
                .toList();
    }
}
