/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mvndaemon.mvnd.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OsUtilsTest {
    @Test
    void kbTohumanReadable() {
        Assertions.assertEquals("0k", OsUtils.kbTohumanReadable(0));
        Assertions.assertEquals("1001k", OsUtils.kbTohumanReadable(1001));
        Assertions.assertEquals("1m", OsUtils.kbTohumanReadable(1024));
        Assertions.assertEquals("1023m", OsUtils.kbTohumanReadable(1024 * 1024 - 1));
        Assertions.assertEquals("1g", OsUtils.kbTohumanReadable(1024 * 1024));
        Assertions.assertEquals("1t", OsUtils.kbTohumanReadable(1024 * 1024 * 1024));
    }

    @Test
    void findJavaHomeFromPath() {
        final String expectedJavaHome = System.getProperty("java.home");
        Assertions.assertEquals(expectedJavaHome, OsUtils.findJavaHomeFromJavaExecutable(expectedJavaHome + "/bin/java"));
    }

}
