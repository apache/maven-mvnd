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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.surefire.api.report.RunMode;

/**
 * Accumulates per-fork test counts and the currently executing class/method. Not thread-safe: Surefire delivers
 * fork-reader events on a single thread per fork channel.
 */
public class TestProgressAccumulator {

    public enum Type {
        TESTSET_STARTING,
        TEST_STARTING,
        TEST_SUCCEEDED,
        TEST_FAILED,
        TEST_ERROR,
        TEST_SKIPPED,
        TESTSET_COMPLETED
    }

    private int completed;
    private int failures;
    private int errors;
    private int skipped;
    private int retrying;
    private int flaky;
    private String testClass;
    private String testMethod;
    private final Map<String, TestState> tests = new LinkedHashMap<>();

    public void record(Type type, String testClass, String testMethod) {
        record(type, testClass, testMethod, RunMode.NORMAL_RUN, null, null);
    }

    public void record(Type type, String testClass, String testMethod, RunMode runMode, Long testRunId) {
        record(type, testClass, testMethod, runMode, testRunId, null);
    }

    public void record(
            Type type, String testClass, String testMethod, RunMode runMode, Long testRunId, String failureMessage) {
        switch (type) {
            case TESTSET_STARTING:
                this.testClass = testClass;
                this.testMethod = null;
                break;
            case TEST_STARTING:
                this.testClass = testClass;
                this.testMethod = testMethod;
                state(testClass, testMethod, testRunId).starting(runMode);
                break;
            case TEST_SUCCEEDED:
                state(testClass, testMethod, testRunId).succeeded();
                break;
            case TEST_FAILED:
                state(testClass, testMethod, testRunId).failed(runMode, sanitize(failureMessage));
                break;
            case TEST_ERROR:
                state(testClass, testMethod, testRunId).errored(runMode, sanitize(failureMessage));
                break;
            case TEST_SKIPPED:
                state(testClass, testMethod, testRunId).skipped();
                break;
            case TESTSET_COMPLETED:
                finalizeRetrying();
                break;
        }
        recompute();
    }

    public int getCompleted() {
        return completed;
    }

    public int getFailures() {
        return failures;
    }

    public int getErrors() {
        return errors;
    }

    public int getSkipped() {
        return skipped;
    }

    public int getRetrying() {
        return retrying;
    }

    public int getFlaky() {
        return flaky;
    }

    public String getTestClass() {
        return testClass;
    }

    public String getTestMethod() {
        return testMethod;
    }

    public List<String> getFlakyTests() {
        List<String> result = new ArrayList<>();
        for (TestState state : tests.values()) {
            if (state.isFlaky()) {
                result.add(state.flakyDetail());
            }
        }
        return result;
    }

    public List<String> getFailedTests() {
        List<String> result = new ArrayList<>();
        for (TestState state : tests.values()) {
            if (state.isFailed()) {
                result.add(state.failureLine());
            }
        }
        return result;
    }

    public List<String> getErroredTests() {
        List<String> result = new ArrayList<>();
        for (TestState state : tests.values()) {
            if (state.isErrored()) {
                result.add(state.failureLine());
            }
        }
        return result;
    }

    private static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String flattened = message.replaceAll("\\s+", " ").trim();
        return flattened.isEmpty() ? null : flattened;
    }

    private TestState state(String testClass, String testMethod, Long testRunId) {
        String key = testRunId != null ? String.valueOf(testRunId) : testClass + "#" + testMethod;
        TestState state = tests.get(key);
        if (state == null) {
            state = new TestState(testClass, testMethod);
            tests.put(key, state);
        } else {
            state.updateName(testClass, testMethod);
        }
        return state;
    }

    private void finalizeRetrying() {
        for (TestState state : tests.values()) {
            state.finalizeRetrying();
        }
    }

    private void recompute() {
        completed = 0;
        failures = 0;
        errors = 0;
        skipped = 0;
        retrying = 0;
        flaky = 0;

        for (TestState state : tests.values()) {
            if (state.skipped) {
                completed++;
                skipped++;
            } else if (state.success) {
                completed++;
                if (state.failure || state.error) {
                    flaky++;
                }
            } else if (state.retrying) {
                retrying++;
            } else if (state.error) {
                completed++;
                errors++;
            } else if (state.failure) {
                completed++;
                failures++;
            }
        }
    }

    private static final class TestState {
        private String testClass;
        private String testMethod;
        private boolean failure;
        private boolean error;
        private boolean success;
        private boolean skipped;
        private boolean retrying;
        private String message;
        private final List<Run> runs = new ArrayList<>();

        private TestState(String testClass, String testMethod) {
            this.testClass = testClass;
            this.testMethod = testMethod;
        }

        private void updateName(String testClass, String testMethod) {
            if (testClass != null) {
                this.testClass = testClass;
            }
            if (testMethod != null) {
                this.testMethod = testMethod;
            }
        }

        private void starting(RunMode runMode) {
            if (runMode == RunMode.RERUN_TEST_AFTER_FAILURE) {
                retrying = true;
            }
        }

        private void succeeded() {
            success = true;
            retrying = false;
            runs.add(Run.pass());
        }

        private void failed(RunMode runMode, String failureMessage) {
            failure = true;
            if (message == null) {
                message = failureMessage;
            }
            runs.add(Run.fail(failureMessage));
            if (runMode == RunMode.RERUN_TEST_AFTER_FAILURE) {
                retrying = true;
            }
        }

        private void errored(RunMode runMode, String failureMessage) {
            error = true;
            if (message == null) {
                message = failureMessage;
            }
            runs.add(Run.error(failureMessage));
            if (runMode == RunMode.RERUN_TEST_AFTER_FAILURE) {
                retrying = true;
            }
        }

        private void skipped() {
            skipped = true;
        }

        private void finalizeRetrying() {
            retrying = false;
        }

        private boolean isFlaky() {
            return success && (failure || error);
        }

        private boolean isErrored() {
            return !skipped && !success && !retrying && error;
        }

        private boolean isFailed() {
            return !skipped && !success && !retrying && !error && failure;
        }

        private String displayName() {
            if (testClass == null) {
                return testMethod;
            }
            String simpleClass = testClass.substring(testClass.lastIndexOf('.') + 1);
            return testMethod != null ? simpleClass + "#" + testMethod : simpleClass;
        }

        private String failureLine() {
            return message != null ? displayName() + ": " + message : displayName();
        }

        private String flakyDetail() {
            StringBuilder sb = new StringBuilder(displayName());
            for (int i = 0; i < runs.size(); i++) {
                sb.append('\n')
                        .append("  Run ")
                        .append(i + 1)
                        .append(": ")
                        .append(runs.get(i).describe());
            }
            return sb.toString();
        }

        private static final class Run {
            private final Outcome outcome;
            private final String message;

            private Run(Outcome outcome, String message) {
                this.outcome = outcome;
                this.message = message;
            }

            private static Run pass() {
                return new Run(Outcome.PASS, null);
            }

            private static Run fail(String message) {
                return new Run(Outcome.FAIL, message);
            }

            private static Run error(String message) {
                return new Run(Outcome.ERROR, message);
            }

            private String describe() {
                if (outcome == Outcome.PASS) {
                    return "PASS";
                }
                if (message != null) {
                    return message;
                }
                return outcome == Outcome.ERROR ? "ERROR" : "FAIL";
            }

            private enum Outcome {
                PASS,
                FAIL,
                ERROR
            }
        }
    }
}
