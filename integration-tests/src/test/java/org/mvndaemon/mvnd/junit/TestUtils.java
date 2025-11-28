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
package org.mvndaemon.mvnd.junit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class TestUtils {
    /**
     * IT circumvention for JDK transport low timeouts and GH macOS runners networking issues.
     * If arguments does not contain settings for timeouts (ie as part of test), they will be
     * added to increase timeouts for IT runs.
     */
    public static List<String> augmentArgs(List<String> args) {
        ArrayList<String> result = new ArrayList<>(args);
        if (result.stream().noneMatch(s -> s.contains("aether.transport.http.connectTimeout"))) {
            result.add("-Daether.transport.http.connectTimeout=1800000");
        }
        // note: def value for requestTimeout=1800000; not setting it as it is fine
        return result;
    }

    public static void replace(Path path, String find, String replacement) {
        try {
            final String originalSrc = Files.readString(path);
            final String newSrc = originalSrc.replace(find, replacement);
            if (originalSrc.equals(newSrc)) {
                throw new IllegalStateException("[" + find + "] not found in " + path);
            }
            Files.writeString(path, newSrc);
        } catch (IOException e) {
            throw new RuntimeException("Could not read or write " + path, e);
        }
    }

    public static Path deleteDir(Path dir) {
        return deleteDir(dir, true);
    }

    public static Path deleteDir(Path dir, boolean failOnError) {
        if (Files.exists(dir)) {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(f -> deleteFile(f, failOnError));
            } catch (Exception e) {
                throw new RuntimeException("Could not walk " + dir, e);
            }
        }
        return dir;
    }

    private static void deleteFile(Path f, boolean failOnError) {
        try {
            Files.delete(f);
        } catch (Exception e) {
            if (failOnError) {
                throw new RuntimeException("Could not delete " + f, e);
            } else {
                System.err.println("Error deleting " + f + ": " + e);
            }
        }
    }
}
