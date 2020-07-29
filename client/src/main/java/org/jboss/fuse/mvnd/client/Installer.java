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

package org.jboss.fuse.mvnd.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Installer {
    private static final Logger LOG = LoggerFactory.getLogger(Installer.class);
    private static final int BUFFER_SIZE = 4096;
    private static final int MAX_PERMISSIONS = 0777;
    private static final int PROGRESS_STEP_BYTES = 1024 * 1024;

    public static void installServer(URI zipUri, Path mvndPropsPath, Path mvndHome, Path javaHome, boolean overwrite) {
        final boolean mvndHomeExists = Files.exists(mvndHome);
        if (!overwrite && mvndHomeExists) {
            throw new IllegalStateException(
                    "Cannot install if mvnd.home " + mvndHome + " exists. Delete it if you want to reinstall.");
        }
        if (!overwrite && Files.exists(mvndPropsPath)) {
            throw new IllegalStateException(
                    "Cannot install if " + mvndPropsPath + " exists. Delete it if you want to reinstall.");
        }
        deleteIfExists(mvndHome);
        deleteIfExists(mvndPropsPath);

        final Path localZip = download(zipUri);
        unzip(localZip, mvndHome);
        writeMvndProperties(mvndPropsPath, mvndHome, javaHome);
    }

    static void deleteIfExists(Path path) {
        if (Files.isRegularFile(path)) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                throw new RuntimeException("Could not delete " + path);
            }
        } else if (Files.isDirectory(path)) {
            try (Stream<Path> files = Files.walk(path)) {
                files.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (Exception e) {
                                throw new RuntimeException("Could not delete " + p, e);
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException("Could not delete " + path, e);
            }
        }
    }

    static void writeMvndProperties(Path mvndPropsPath, Path mvndHome, Path javaHome) {
        LOG.info("Writing {}", mvndPropsPath);
        final String template = readTemplate();
        final String javaHomeLine = javaHome == null ? "" : "java.home = " + javaHome.toString();
        final String content = String.format(template, mvndHome.toString(), javaHomeLine);
        try {
            Files.write(mvndPropsPath, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Could not write to " + mvndPropsPath);
        }
    }

    static String readTemplate() {
        try (InputStream in = Installer.class.getResourceAsStream("mvnd.properties.template");
                ByteArrayOutputStream out = new ByteArrayOutputStream(256)) {
            copy(in, out, false);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not read build.properties");
        }
    }

    static void unzip(Path localZip, Path destinationDir) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Unpacking {} to {}", localZip, destinationDir);
        } else {
            LOG.info("Unpacking to {}", destinationDir);
        }
        try {
            Files.createDirectories(destinationDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create directories " + destinationDir, e);
        }
        try (ZipFile zip = new ZipFile(Files.newByteChannel(localZip))) {
            final Map<Integer, Set<PosixFilePermission>> permissionCache = new HashMap<>();
            final Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                final ZipArchiveEntry entry = entries.nextElement();
                final Path dest = destinationDir.resolve(entry.getName()).normalize();
                if (!dest.startsWith(destinationDir)) {
                    /* Avoid writing to paths outside of mvndHome */
                    throw new IllegalStateException("Possibly tainted ZIP entry name " + entry.getName()
                            + " would have to be unpacked outside of " + destinationDir);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    try (
                            InputStream in = new BufferedInputStream(zip.getInputStream(entry), BUFFER_SIZE);
                            OutputStream out = new BufferedOutputStream(Files.newOutputStream(dest), BUFFER_SIZE)) {
                        copy(in, out, false);
                    } catch (IOException e) {
                        throw new RuntimeException(
                                "Could not unzip entry " + entry.getName() + " from " + localZip + " to " + dest);
                    }
                }
                final int mode = (int) (entry.getUnixMode() & MAX_PERMISSIONS);
                if (mode != 0) {
                    final PosixFileAttributeView attributes = Files.getFileAttributeView(dest, PosixFileAttributeView.class);
                    if (attributes != null) {
                        Files.setPosixFilePermissions(dest, permissionCache.computeIfAbsent(mode, Installer::toPermissionSet));
                    }
                }
                Files.setLastModifiedTime(dest, FileTime.from(entry.getTime(), TimeUnit.MILLISECONDS));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not unzip " + localZip + " to " + destinationDir, e);
        }
    }

    static Path download(URI zipUri) {
        try {
            final Path localZip = Files.createTempFile("", "-mvnd-dist.zip");
            if (LOG.isDebugEnabled()) {
                LOG.debug("Downloading {} to {}", zipUri, localZip);
            } else {
                LOG.info("Downloading {}", zipUri);
            }
            try (
                    InputStream in = new BufferedInputStream(zipUri.toURL().openStream(), BUFFER_SIZE);
                    OutputStream out = new BufferedOutputStream(Files.newOutputStream(localZip), BUFFER_SIZE)) {
                copy(in, out, LOG.isInfoEnabled());
            } catch (IOException e) {
                throw new RuntimeException("Could not download " + zipUri + " to " + localZip, e);
            }
            return localZip;
        } catch (IOException e) {
            throw new RuntimeException("Could not create temp file", e);
        }
    }

    static void copy(InputStream in, OutputStream out, boolean printProgress) throws IOException {
        final byte buf[] = new byte[BUFFER_SIZE];
        int len;
        long byteCount = 0;
        long nextStep = PROGRESS_STEP_BYTES;
        while ((len = in.read(buf)) >= 0) {
            if (printProgress) {
                byteCount += len;
                if (byteCount >= nextStep) {
                    nextStep += PROGRESS_STEP_BYTES;
                    System.out.print('.');
                }
            }
            out.write(buf, 0, len);
        }
        if (printProgress) {
            System.out.println();
        }
    }

    static Set<PosixFilePermission> toPermissionSet(Integer mode) {
        final Set<PosixFilePermission> result = EnumSet.noneOf(PosixFilePermission.class);
        /* others */
        if ((mode & 0001) != 0) {
            result.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        if ((mode & 0002) != 0) {
            result.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((mode & 0004) != 0) {
            result.add(PosixFilePermission.OTHERS_READ);
        }
        /* group */
        if ((mode & 0010) != 0) {
            result.add(PosixFilePermission.GROUP_EXECUTE);
        }
        if ((mode & 0020) != 0) {
            result.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((mode & 0040) != 0) {
            result.add(PosixFilePermission.GROUP_READ);
        }
        /* user */
        if ((mode & 0100) != 0) {
            result.add(PosixFilePermission.OWNER_EXECUTE);
        }
        if ((mode & 0200) != 0) {
            result.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((mode & 0400) != 0) {
            result.add(PosixFilePermission.OWNER_READ);
        }
        return Collections.unmodifiableSet(result);
    }

}
