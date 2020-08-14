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
package org.jboss.fuse.mvnd.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DaemonRegistryTest {

    @Test
    public void testReadWrite() throws IOException {
        Path temp = File.createTempFile("reg", ".data").toPath();
        DaemonRegistry reg1 = new DaemonRegistry(temp);
        DaemonRegistry reg2 = new DaemonRegistry(temp);

        assertNotNull(reg1.getAll());
        assertEquals(0, reg1.getAll().size());
        assertNotNull(reg2.getAll());
        assertEquals(0, reg2.getAll().size());

        byte[] token = new byte[16];
        new Random().nextBytes(token);
        reg1.store(new DaemonInfo("the-uid", "/java/home/",
                "/data/reg/", 0x12345678, 7502, 65536,
                Locale.getDefault().toLanguageTag(), Arrays.asList("-Xmx"),
                DaemonState.Idle, System.currentTimeMillis(), System.currentTimeMillis()));

        assertNotNull(reg1.getAll());
        assertEquals(1, reg1.getAll().size());
        assertNotNull(reg2.getAll());
        assertEquals(1, reg2.getAll().size());
    }

}
