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

public class Installer {
    private static final int BUFFER_SIZE = 4096;
    private static final int MAX_PERMISSIONS = 0777;
    public static void installServer(URI zipUri, Path mvndPropsPath, Path mvndHome, Path javaHome, boolean overwrite) {
        final boolean mvndHomeExists = Files.exists(mvndHome);
        if (!overwrite && mvndHomeExists) {
            throw new IllegalStateException(
                    "Cannot install if mvnd.home " + mvndHome + " exists. Consider --update instead of --install.");
        }
        if (!overwrite && Files.exists(mvndPropsPath)) {
            throw new IllegalStateException(
                    "Cannot install if " + mvndPropsPath + " exists. Consider --update instead of --install.");
        }
        deleteIfExists(mvndHome);
        deleteIfExists(mvndPropsPath);

        final Path localZip = download(zipUri);
        unzip(localZip, mvndHome);
        writeMvndProperties(mvndPropsPath, mvndHome, javaHome);
    }

    private static void deleteIfExists(Path path) {
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
            copy(in, out);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not read build.properties");
        }
    }

    static void unzip(Path localZip, Path mvndHome) {
        try {
            Files.createDirectories(mvndHome);
        } catch (IOException e) {
            throw new RuntimeException("Could not create directories " + mvndHome, e);
        }
        try (ZipFile zip = new ZipFile(Files.newByteChannel(localZip))) {
            final Map<Integer, Set<PosixFilePermission>> permissionCache = new HashMap<>();
            final Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                final ZipArchiveEntry entry = entries.nextElement();
                final Path dest = mvndHome.resolve(entry.getName()).normalize();
                if (!dest.startsWith(mvndHome)) {
                    /* Avoid writing to paths outside of mvndHome */
                    throw new IllegalStateException("Tainted ZIP entry name " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    try (
                            InputStream in = new BufferedInputStream(zip.getInputStream(entry), BUFFER_SIZE);
                            OutputStream out = new BufferedOutputStream(Files.newOutputStream(dest), BUFFER_SIZE)) {
                        copy(in, out);
                    } catch (IOException e) {
                        throw new RuntimeException(
                                "Could not unzip entry " + entry.getName() + " from " + localZip + " to " + dest);
                    }
                }
                final PosixFileAttributeView attributes = Files.getFileAttributeView(dest, PosixFileAttributeView.class);
                if (attributes != null) {
                    int mode = (int) (entry.getUnixMode() & MAX_PERMISSIONS);
                    Files.setPosixFilePermissions(dest, permissionCache.computeIfAbsent(mode, Installer::toPermissionSet));
                }
                Files.setLastModifiedTime(dest, FileTime.from(entry.getTime(), TimeUnit.MILLISECONDS));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not unzip " + localZip, e);
        }
    }

    static Path download(URI zipUri) {
        try {
            final Path localZip = Files.createTempFile("", "-mvnd-dist.zip");
            try (
                    InputStream in = new BufferedInputStream(zipUri.toURL().openStream(), BUFFER_SIZE);
                    OutputStream out = new BufferedOutputStream(Files.newOutputStream(localZip), BUFFER_SIZE)) {
                copy(in, out);
            } catch (IOException e) {
                throw new RuntimeException("Could not download " + zipUri + " to " + localZip);
            }
            return localZip;
        } catch (IOException e) {
            throw new RuntimeException("Could not create temp file", e);
        }
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        final byte buf[] = new byte[BUFFER_SIZE];
        int len;
        while ((len = in.read(buf)) >= 0) {
            out.write(buf, 0, len);
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
