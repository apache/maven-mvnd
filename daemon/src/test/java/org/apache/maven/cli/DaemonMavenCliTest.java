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
package org.apache.maven.cli;

import java.io.File;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DaemonMavenCliTest {

    @Test
    void testChdir() throws Exception {
        File d = new File("target/tstDir").getAbsoluteFile();
        d.mkdirs();
        DaemonMavenCli.chDir(d.toString());
        assertEquals(new File(d, "test").getAbsolutePath(), new File("test").getAbsolutePath());
        assertEquals(d.toPath().resolve("test").toAbsolutePath().toString(),
                Paths.get("test").toAbsolutePath().toString());
    }

}
