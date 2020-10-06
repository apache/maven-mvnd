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
package org.jboss.fuse.mvnd.junit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public class TestUtils {

    public static void replace(Path path, String find, String replacement) {
        try {
            final String originalSrc = Files.readString(path);
            final String newSrc = originalSrc.replace(find, replacement);
            if (originalSrc.equals(newSrc)) {
                throw new IllegalStateException("[" + find + "] not found in " + path);
            }
            Files.write(path, newSrc.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Could not read or write " + path, e);
        }
    }

    public static Path deleteDir(Path dir) {
        if (Files.exists(dir)) {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder())
                        .forEach(f -> {
                            try {
                                Files.delete(f);
                            } catch (IOException e) {
                                throw new RuntimeException("Could not delete " + f);
                            }
                        });
            } catch (IOException e1) {
                throw new RuntimeException("Could not walk " + dir);
            }
        }
        return dir;
    }

    public static void extractLocalMavenRepo(Path localMavenRepo) throws IOException {
        deleteDir(localMavenRepo);
        Files.createDirectories(localMavenRepo);
        try (TarArchiveInputStream tis = new TarArchiveInputStream(
                new GZIPInputStream(TestUtils.class.getResourceAsStream("/local-maven-repo.tar.gz"), 8192))) {
            TarArchiveEntry te;
            while ((te = tis.getNextTarEntry()) != null) {
                Path path = localMavenRepo.resolve(te.getName());
                if (te.isDirectory()) {
                    Files.createDirectories(path);
                } else {
                    Files.copy(tis, path);
                }
            }
        }
    }
}
