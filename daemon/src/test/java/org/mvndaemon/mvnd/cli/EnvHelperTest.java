/*
 * Copyright 2021 the original author or authors.
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
package org.mvndaemon.mvnd.cli;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.common.Os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class EnvHelperTest {

    @Test
    void testSetEnv() throws Exception {
        String id = "Aa" + UUID.randomUUID();
        assertNull(System.getenv(id));
        assertNull(System.getenv().get(id));
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put(id, id);
        EnvHelper.environment(System.getProperty("user.dir"), env);
        assertEquals(id, System.getenv(id));
        assertEquals(id, System.getenv().get(id));
        assertEquals(Os.current() == Os.WINDOWS ? id : null, System.getenv(id.toLowerCase(Locale.ROOT)));
        assertEquals(Os.current() == Os.WINDOWS ? id : null, System.getenv().get(id.toLowerCase(Locale.ROOT)));
        assertEquals(Os.current() == Os.WINDOWS ? id : null, System.getenv(id.toUpperCase(Locale.ROOT)));
        assertEquals(Os.current() == Os.WINDOWS ? id : null, System.getenv().get(id.toUpperCase(Locale.ROOT)));
        env.remove(id);
        EnvHelper.environment(System.getProperty("user.dir"), env);
        assertNull(System.getenv(id));
        assertNull(System.getenv().get(id));
    }

    @Test
    void testChdir() throws Exception {
        File d = new File("target/tstDir").getAbsoluteFile();
        d.mkdirs();
        EnvHelper.chDir(d.toString());
        assertEquals(new File(d, "test").getAbsolutePath(), new File("test").getAbsolutePath());
        assertEquals(d.toPath().resolve("test").toAbsolutePath().toString(),
                Paths.get("test").toAbsolutePath().toString());
    }

    @Test
    @Disabled
    void testCygwin() throws Exception {
        assertEquals("/cygdrive/c/work/tmp/", EnvHelper.toCygwin("C:\\work\\tmp\\"));
    }

}
