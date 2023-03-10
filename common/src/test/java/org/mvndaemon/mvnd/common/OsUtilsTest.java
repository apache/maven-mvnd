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
package org.mvndaemon.mvnd.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OsUtilsTest {

    @Test
    void bytesToHumanReadable() {
        Assertions.assertEquals("0B", OsUtils.bytesToHumanReadable(0L));
        Assertions.assertEquals("1001B", OsUtils.bytesToHumanReadable(1001L));
        Assertions.assertEquals("1k", OsUtils.bytesToHumanReadable(1024L));
        Assertions.assertEquals("1023k", OsUtils.bytesToHumanReadable(1024L * 1024L - 1L));
        Assertions.assertEquals("1m", OsUtils.bytesToHumanReadable(1024L * 1024L));
        Assertions.assertEquals("1g", OsUtils.bytesToHumanReadable(1024L * 1024L * 1024L));
        Assertions.assertEquals("1t", OsUtils.bytesToHumanReadable(1024L * 1024L * 1024L * 1024L));
    }

    @Test
    void kbToHumanReadable() {
        Assertions.assertEquals("0k", OsUtils.kbToHumanReadable(0L));
        Assertions.assertEquals("1001k", OsUtils.kbToHumanReadable(1001L));
        Assertions.assertEquals("1m", OsUtils.kbToHumanReadable(1024L));
        Assertions.assertEquals("1023m", OsUtils.kbToHumanReadable(1024L * 1024L - 1L));
        Assertions.assertEquals("1g", OsUtils.kbToHumanReadable(1024L * 1024L));
        Assertions.assertEquals("1t", OsUtils.kbToHumanReadable(1024L * 1024L * 1024L));
    }

    @Test
    void findJavaHomeFromPath() {
        final String expectedJavaHome = System.getProperty("java.home");
        Assertions.assertEquals(
                expectedJavaHome, OsUtils.findJavaHomeFromJavaExecutable(expectedJavaHome + "/bin/java"));
    }
}
