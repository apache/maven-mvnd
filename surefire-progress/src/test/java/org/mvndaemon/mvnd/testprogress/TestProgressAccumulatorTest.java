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
package org.mvndaemon.mvnd.testprogress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mvndaemon.mvnd.testprogress.TestProgressAccumulator.Type.TESTSET_STARTING;
import static org.mvndaemon.mvnd.testprogress.TestProgressAccumulator.Type.TEST_ERROR;
import static org.mvndaemon.mvnd.testprogress.TestProgressAccumulator.Type.TEST_FAILED;
import static org.mvndaemon.mvnd.testprogress.TestProgressAccumulator.Type.TEST_SKIPPED;
import static org.mvndaemon.mvnd.testprogress.TestProgressAccumulator.Type.TEST_STARTING;
import static org.mvndaemon.mvnd.testprogress.TestProgressAccumulator.Type.TEST_SUCCEEDED;

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
    void testsetStartingSetsClassWithNullMethod() {
        TestProgressAccumulator acc = new TestProgressAccumulator();
        acc.record(TESTSET_STARTING, "OtherTest", null);
        assertEquals("OtherTest", acc.getTestClass());
        assertNull(acc.getTestMethod());
    }
}
