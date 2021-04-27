/*
 * Copyright (C) 2009-2021 the original author(s).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mvndaemon.mvnd.nativ;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CLibraryTest {

    @Test
    void testChdir() {
        File d = new File("target/tstDir");
        d.mkdirs();
        CLibrary.chdir(d.getAbsolutePath());
    }

    @Test
    void testSetenv() {
        CLibrary.setenv("MY_NAME", "myValue");
    }

    @Test
    @EnabledOnOs(OS.MAC)
    void testOsxMemory() {
        long[] mem = new long[2];
        assertEquals(0, CLibrary.getOsxMemoryInfo(mem));
        assertTrue(mem[0] > 1024, "Total: " + mem[0]);
        assertTrue(mem[1] > 1024, "Free: " + mem[1]);
        assertTrue(mem[1] < mem[0], "Free (" + mem[1] + ") < Total (" + mem[0] + ")");
    }
}
