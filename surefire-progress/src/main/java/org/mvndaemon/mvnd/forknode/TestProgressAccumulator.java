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
    private String testClass;
    private String testMethod;

    public void record(Type type, String testClass, String testMethod) {
        switch (type) {
            case TESTSET_STARTING:
                this.testClass = testClass;
                this.testMethod = null;
                break;
            case TEST_STARTING:
                this.testClass = testClass;
                this.testMethod = testMethod;
                break;
            case TEST_SUCCEEDED:
                completed++;
                break;
            case TEST_FAILED:
                completed++;
                failures++;
                break;
            case TEST_ERROR:
                completed++;
                errors++;
                break;
            case TEST_SKIPPED:
                completed++;
                skipped++;
                break;
            case TESTSET_COMPLETED:
                break;
        }
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

    public String getTestClass() {
        return testClass;
    }

    public String getTestMethod() {
        return testMethod;
    }
}
