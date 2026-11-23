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

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersionInfoTest {

    private static Properties sys() {
        Properties p = new Properties();
        p.setProperty("java.version", "11.0.1");
        p.setProperty("java.vendor", "ClientVendor");
        p.setProperty("java.home", "/client/jdk");
        p.setProperty("file.encoding", "UTF-8");
        p.setProperty("os.name", "Mac OS X");
        p.setProperty("os.version", "14.1");
        p.setProperty("os.arch", "aarch64");
        return p;
    }

    @Test
    void readsMavenAndJavaDetailsFromTheDistribution(@TempDir Path dir) throws Exception {
        Path lib = Files.createDirectories(dir.resolve("mvn/lib"));
        try (OutputStream os = Files.newOutputStream(lib.resolve("maven-core-3.9.9.jar"));
                ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.putNextEntry(new ZipEntry("org/apache/maven/messages/build.properties"));
            zip.write("version=3.9.9\nbuildNumber=abc123\ndistributionName=Apache Maven\n"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Path jdk = Files.createDirectories(dir.resolve("jdk"));
        Files.write(
                jdk.resolve("release"),
                "JAVA_VERSION=\"17.0.20\"\nIMPLEMENTOR=\"Amazon.com Inc.\"\n".getBytes(StandardCharsets.UTF_8));

        Path mavenHome = dir.resolve("mvn");
        List<String> lines = VersionInfo.lines(lib, "0.0", mavenHome, jdk, sys());

        assertEquals("Apache Maven 3.9.9 (abc123)", lines.get(0));
        assertEquals("Maven home: " + mavenHome, lines.get(1));
        assertEquals("Java version: 17.0.20, vendor: Amazon.com Inc., runtime: " + jdk, lines.get(2));
        assertTrue(lines.get(3).startsWith("Default locale: "));
        assertTrue(lines.get(3)
                .endsWith(", platform encoding: UTF-8, time zone: "
                        + ZoneId.systemDefault().getId()));
        assertEquals("OS name: \"mac os x\", version: \"14.1\", arch: \"aarch64\", family: \"mac\"", lines.get(4));
        assertEquals(5, lines.size());
    }

    @Test
    void fallsBackToTheEmbeddedVersionAndClientJvm(@TempDir Path dir) {
        List<String> lines = VersionInfo.lines(dir.resolve("missing"), "3.9.16", dir, null, sys());

        assertEquals("Apache Maven 3.9.16 (embedded)", lines.get(0));
        assertEquals("Java version: 11.0.1, vendor: ClientVendor, runtime: /client/jdk", lines.get(2));
    }

    @Test
    void partialReleaseFileNeverFallsBackToTheClientJvm(@TempDir Path dir) throws Exception {
        Path jdk = Files.createDirectories(dir.resolve("jdk"));
        Files.write(jdk.resolve("release"), "JAVA_VERSION=\"17.0.20\"\n".getBytes(StandardCharsets.UTF_8));

        List<String> lines = VersionInfo.lines(null, "3.9.16", dir, jdk, sys());
        assertEquals("Java version: 17.0.20, vendor: <unknown vendor>, runtime: " + jdk, lines.get(2));

        Path noRelease = Files.createDirectories(dir.resolve("jdk2"));
        lines = VersionInfo.lines(null, "3.9.16", dir, noRelease, sys());
        assertEquals(
                "Java version: <unknown Java version>, vendor: <unknown vendor>, runtime: " + noRelease, lines.get(2));
    }

    @Test
    void platformEncodingIsFileEncodingOnly() {
        Properties sys = sys();
        sys.setProperty("native.encoding", "ISO-8859-2");
        assertTrue(VersionInfo.lines(null, "3.9.16", Paths.get("m"), null, sys)
                .get(3)
                .contains(", platform encoding: UTF-8, time zone: "));
        sys.remove("file.encoding");
        assertTrue(VersionInfo.lines(null, "3.9.16", Paths.get("m"), null, sys)
                .get(3)
                .contains(", platform encoding: <unknown encoding>, time zone: "));
    }

    @Test
    void osLineIsLowerCased() {
        Properties sys = sys();
        sys.setProperty("os.version", "10.0 SP1");
        sys.setProperty("os.arch", "AMD64");
        assertEquals(
                "OS name: \"mac os x\", version: \"10.0 sp1\", arch: \"amd64\", family: \"mac\"",
                VersionInfo.lines(null, "3.9.16", Paths.get("m"), null, sys).get(4));
    }

    @Test
    void plainVersionRequest() {
        assertTrue(VersionInfo.isPlainVersionRequest(Arrays.asList("-v")));
        assertTrue(VersionInfo.isPlainVersionRequest(Arrays.asList("--version", "-V")));
        for (String other : Arrays.asList("-q", "--quiet", "-h", "--help", "--status", "--stop", "--purge")) {
            assertFalse(VersionInfo.isPlainVersionRequest(Arrays.asList("-v", other)), other);
        }
        assertFalse(VersionInfo.isPlainVersionRequest(Arrays.asList("clean")));
    }

    @Test
    void osFamily() {
        assertEquals("windows", VersionInfo.osFamily("Windows 11"));
        assertEquals("mac", VersionInfo.osFamily("Mac OS X"));
        assertEquals("unix", VersionInfo.osFamily("Linux"));
    }
}
