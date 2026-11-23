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
package org.mvndaemon.mvnd.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.cli.internal.extension.model.CoreExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreExtensionsTest {

    @Test
    void projectWinsOverUserWinsOverInstallation(@TempDir Path dir) throws Exception {
        Path project = write(dir.resolve("project.xml"), ext("g", "a", "1") + ext("g", "p", "1"));
        Path user = write(dir.resolve("user.xml"), ext("g", "a", "2") + ext("g", "u", "2"));
        Path installation = write(dir.resolve("installation.xml"), ext("g", "u", "3") + ext("g", "i", "3"));

        List<String> ids =
                DaemonParameters.readCoreExtensions(project, user, dir.resolve("missing.xml"), installation).stream()
                        .map(CoreExtension::getId)
                        .collect(Collectors.toList());

        assertEquals(List.of("g:a:1", "g:p:1", "g:u:2", "g:i:3"), ids);
    }

    @Test
    void noFiles() throws Exception {
        assertEquals(List.of(), DaemonParameters.readCoreExtensions((Path) null));
    }

    private static String ext(String g, String a, String v) {
        return "<extension><groupId>" + g + "</groupId><artifactId>" + a + "</artifactId><version>" + v
                + "</version></extension>";
    }

    private static Path write(Path file, String extensions) throws Exception {
        return Files.write(file, ("<extensions>" + extensions + "</extensions>").getBytes(StandardCharsets.UTF_8));
    }
}
