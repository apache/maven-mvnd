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
package org.mvndaemon.mvnd.forknode;

import org.apache.maven.surefire.api.report.RunMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mvndaemon.mvnd.forknode.TestProgressAccumulator.Type.TESTSET_COMPLETED;
import static org.mvndaemon.mvnd.forknode.TestProgressAccumulator.Type.TESTSET_STARTING;
import static org.mvndaemon.mvnd.forknode.TestProgressAccumulator.Type.TEST_ERROR;
import static org.mvndaemon.mvnd.forknode.TestProgressAccumulator.Type.TEST_FAILED;
import static org.mvndaemon.mvnd.forknode.TestProgressAccumulator.Type.TEST_SKIPPED;
import static org.mvndaemon.mvnd.forknode.TestProgressAccumulator.Type.TEST_STARTING;
import static org.mvndaemon.mvnd.forknode.TestProgressAccumulator.Type.TEST_SUCCEEDED;

class TestProgressAccumulatorTest {

    @Test
    void countsPassingTests() {
        TestProgressAccumulator acc = new TestProgressAccumulator();
        acc.record(TESTSET_STARTING, "MyServiceTest", null);
        acc.record(TEST_STARTING, "MyServiceTest", "shouldWork");
        acc.record(TEST_SUCCEEDED, "MyServiceTest", "shouldWork");
        acc.record(TEST_STARTING, "MyServiceTest", "alsoWorks");
        acc.record(TEST_SUCCEEDED, "MyServiceTest", "alsoWorks");

        assertEquals(2, acc.getCompleted());
        assertEquals(0, acc.getFailures());
        assertEquals(0, acc.getErrors());
        assertEquals(0, acc.getSkipped());
        assertEquals("MyServiceTest", acc.getTestClass());
        assertEquals("alsoWorks", acc.getTestMethod());
    }

    @Test
    void countsFailuresErrorsAndSkips() {
        TestProgressAccumulator acc = new TestProgressAccumulator();
        acc.record(TEST_STARTING, "T", "a");
        acc.record(TEST_FAILED, "T", "a");
        acc.record(TEST_STARTING, "T", "b");
        acc.record(TEST_ERROR, "T", "b");
        acc.record(TEST_STARTING, "T", "c");
        acc.record(TEST_SKIPPED, "T", "c");

        assertEquals(3, acc.getCompleted());
        assertEquals(1, acc.getFailures());
        assertEquals(1, acc.getErrors());
        assertEquals(1, acc.getSkipped());
    }

    @Test
    void failedAndErroredTestsExposeNameAndMessage() {
        TestProgressAccumulator acc = new TestProgressAccumulator();
        acc.record(TEST_STARTING, "org.example.CalcTest", "adds", RunMode.NORMAL_RUN, null);
        acc.record(TEST_FAILED, "org.example.CalcTest", "adds", RunMode.NORMAL_RUN, null, "expected: <5> but was: <4>");
        acc.record(TEST_STARTING, "org.example.CalcTest", "divides", RunMode.NORMAL_RUN, null);
        acc.record(TEST_ERROR, "org.example.CalcTest", "divides", RunMode.NORMAL_RUN, null, "/ by zero");

        assertEquals(1, acc.getFailures());
        assertEquals(1, acc.getErrors());
        assertEquals(java.util.List.of("CalcTest#adds: expected: <5> but was: <4>"), acc.getFailedTests());
        assertEquals(java.util.List.of("CalcTest#divides: / by zero"), acc.getErroredTests());
    }

    @Test
    void firstFailureMessageWinsAndNewlinesAreFlattened() {
        TestProgressAccumulator acc = new TestProgressAccumulator();
        acc.record(TEST_STARTING, "T", "a", RunMode.NORMAL_RUN, null);
        acc.record(TEST_FAILED, "T", "a", RunMode.NORMAL_RUN, null, "line one\n  line two");

        assertEquals(java.util.List.of("T#a: line one line two"), acc.getFailedTests());
    }

    @Test
    void flakyTestIsNeitherFailedNorErrored() {
        TestProgressAccumulator acc = new TestProgressAccumulator();
        acc.record(TEST_STARTING, "T", "a", RunMode.NORMAL_RUN, 1L);
        acc.record(TEST_FAILED, "T", "a", RunMode.NORMAL_RUN, 1L, "boom");
        acc.record(TEST_STARTING, "T", "a", RunMode.RERUN_TEST_AFTER_FAILURE, 1L);
        acc.record(TEST_SUCCEEDED, "T", "a", RunMode.RERUN_TEST_AFTER_FAILURE, 1L);

        assertTrue(acc.getFailedTests().isEmpty(), "a recovered test must not be listed as failed");
        assertTrue(acc.getErroredTests().isEmpty(), "a recovered test must not be listed as errored");
        assertTrue(acc.getFlakyTests().contains("T#a"));
    }

    @Test
    void testsetStartingSetsClassWithNullMethod() {
        TestProgressAccumulator acc = new TestProgressAccumulator();
        acc.record(TESTSET_STARTING, "OtherTest", null);
        assertEquals("OtherTest", acc.getTestClass());
        assertNull(acc.getTestMethod());
    }

    @Test
    void retriesCanRecoverAsFlakyTests() {
        TestProgressAccumulator acc = new TestProgressAccumulator();
        acc.record(TEST_STARTING, "MyServiceTest", "shouldWork", RunMode.NORMAL_RUN, 1L);
        acc.record(TEST_FAILED, "MyServiceTest", "shouldWork", RunMode.NORMAL_RUN, 1L);
        assertEquals(1, acc.getFailures());
        assertEquals(0, acc.getRetrying());

        acc.record(TEST_STARTING, "MyServiceTest", "shouldWork", RunMode.RERUN_TEST_AFTER_FAILURE, 1L);
        assertEquals(0, acc.getFailures());
        assertEquals(1, acc.getRetrying());

        acc.record(TEST_SUCCEEDED, "MyServiceTest", "shouldWork", RunMode.RERUN_TEST_AFTER_FAILURE, 1L);

        assertEquals(1, acc.getCompleted());
        assertEquals(0, acc.getFailures());
        assertEquals(0, acc.getRetrying());
        assertEquals(1, acc.getFlaky());
        assertTrue(acc.getFlakyTests().contains("MyServiceTest#shouldWork"));
    }

    @Test
    void unrecoveredRetryEndsAsFailure() {
        TestProgressAccumulator acc = new TestProgressAccumulator();
        acc.record(TEST_STARTING, "MyServiceTest", "shouldWork", RunMode.NORMAL_RUN, 1L);
        acc.record(TEST_FAILED, "MyServiceTest", "shouldWork", RunMode.NORMAL_RUN, 1L);
        acc.record(TEST_STARTING, "MyServiceTest", "shouldWork", RunMode.RERUN_TEST_AFTER_FAILURE, 1L);
        assertEquals(1, acc.getRetrying());

        acc.record(TESTSET_COMPLETED, "MyServiceTest", null, RunMode.RERUN_TEST_AFTER_FAILURE, 1L);

        assertEquals(1, acc.getCompleted());
        assertEquals(1, acc.getFailures());
        assertEquals(0, acc.getRetrying());
        assertEquals(0, acc.getFlaky());
    }
}
