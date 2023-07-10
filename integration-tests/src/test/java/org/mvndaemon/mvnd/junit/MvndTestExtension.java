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
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.common.DaemonRegistry;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.TimeUtils;

import static org.mvndaemon.mvnd.junit.TestParameters.TEST_MIN_THREADS;
import static org.mvndaemon.mvnd.junit.TestUtils.deleteDir;

public class MvndTestExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {

    private static final Logger LOG = Logger.getLogger(MvndTestExtension.class);

    /** A placeholder to replace with a temporary directory outside of the current source tree */
    public static final String TEMP_EXTERNAL = "${temp.external}";

    private volatile Exception bootException;

    public MvndTestExtension() {
        super();
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        try {
            final Store store = context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
            final Class<?> testClass = context.getRequiredTestClass();
            final MvndTest mvndTest = testClass.getAnnotation(MvndTest.class);
            String keepAlive = Environment.MVND_KEEP_ALIVE.getDefault();
            if (mvndTest != null) {
                store.put(
                        MvndResource.class.getName(),
                        MvndResource.create(
                                context.getRequiredTestClass().getSimpleName(),
                                mvndTest.projectDir(),
                                false,
                                -1L,
                                mvndTest.keepAlive(),
                                mvndTest.maxLostKeepAlive()));
            } else {
                final MvndNativeTest mvndNativeTest = testClass.getAnnotation(MvndNativeTest.class);
                store.put(
                        MvndResource.class.getName(),
                        MvndResource.create(
                                context.getRequiredTestClass().getSimpleName(),
                                mvndNativeTest.projectDir(),
                                true,
                                mvndNativeTest.timeoutSec() * 1000L,
                                mvndNativeTest.keepAlive(),
                                mvndNativeTest.maxLostKeepAlive()));
            }
        } catch (Exception e) {
            this.bootException = e;
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (bootException != null) {
            throw new Exception("Could not init " + context.getRequiredTestClass(), bootException);
        }
        final Store store = context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
        final MvndResource resource = (MvndResource) store.get(MvndResource.class.getName());

        final Object testInstance = context.getRequiredTestInstance();
        Class<?> c = testInstance.getClass();
        while (c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                javax.inject.Inject inject = f.getAnnotation(javax.inject.Inject.class);
                if (inject != null) {
                    f.setAccessible(true);
                    if (DaemonRegistry.class.isAssignableFrom(f.getType())) {
                        f.set(testInstance, resource.registry);
                    } else if (DaemonParameters.class.isAssignableFrom(f.getType())) {
                        f.set(testInstance, resource.parameters);
                    } else if (f.getType() == Client.class) {
                        f.set(testInstance, newClient(resource.isNative, resource.parameters, resource.timeoutMs));
                    } else if (f.getType() == ClientFactory.class) {
                        final ClientFactory cf =
                                customParameters -> newClient(resource.isNative, customParameters, resource.timeoutMs);
                        f.set(testInstance, cf);
                    }
                }
            }
            c = c.getSuperclass();
        }
    }

    Client newClient(boolean isNative, DaemonParameters parameters, long timeoutMs) {
        if (isNative) {
            final Path mvndNativeExecutablePath = parameters
                    .mvndHome()
                    .resolve(
                            System.getProperty("os.name")
                                            .toLowerCase(Locale.ROOT)
                                            .startsWith("windows")
                                    ? "bin/mvnd.exe"
                                    : "bin/mvnd")
                    .toAbsolutePath()
                    .normalize();
            if (!Files.isRegularFile(mvndNativeExecutablePath)) {
                throw new IllegalStateException("mvnd executable does not exist: " + mvndNativeExecutablePath);
            }
            return new NativeTestClient(parameters, mvndNativeExecutablePath, timeoutMs);
        } else {
            return new JvmTestClient(parameters);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        final Store store = context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
        final MvndResource resource = (MvndResource) store.remove(MvndResource.class.getName());
        if (resource != null) {
            resource.close();
        }
    }

    static class MvndResource implements ExtensionContext.Store.CloseableResource {

        private final TestParameters parameters;
        private final TestRegistry registry;
        private final boolean isNative;
        private final long timeoutMs;

        public static MvndResource create(
                String className,
                String rawProjectDir,
                boolean isNative,
                long timeoutMs,
                String keepAlive,
                String maxLostKeepAlive)
                throws IOException {
            if (rawProjectDir == null) {
                throw new IllegalStateException("rawProjectDir of @MvndTest must be set");
            }
            final Path testDir = Paths.get("target/mvnd-tests/" + className).toAbsolutePath();
            deleteDir(testDir);
            Files.createDirectories(testDir);
            final Path testExecutionDir;
            if (TEMP_EXTERNAL.equals(rawProjectDir)) {
                try {
                    testExecutionDir = Files.createTempDirectory(MvndTestExtension.class.getSimpleName());
                } catch (IOException e) {
                    throw new RuntimeException("Could not create temporary directory", e);
                }
            } else {
                final Path mvndTestSrcDir =
                        Paths.get(rawProjectDir).toAbsolutePath().normalize();
                if (!Files.exists(mvndTestSrcDir)) {
                    throw new IllegalStateException("@MvndTest(projectDir = \"" + mvndTestSrcDir
                            + "\") points at a path that does not exist: " + mvndTestSrcDir);
                }
                testExecutionDir = testDir.resolve("project");
                try (Stream<Path> files = Files.walk(mvndTestSrcDir)) {
                    files.forEach(source -> {
                        final Path dest = testExecutionDir.resolve(mvndTestSrcDir.relativize(source));
                        try {
                            if (Files.isDirectory(source)) {
                                Files.createDirectories(dest);
                            } else {
                                Files.createDirectories(dest.getParent());
                                Files.copy(source, dest);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }

                final Path dotMvn = testExecutionDir.resolve(".mvn");
                if (!Files.exists(dotMvn)) {
                    Files.createDirectories(dotMvn);
                }
            }
            final Path multiModuleProjectDirectory =
                    Paths.get(DaemonParameters.findDefaultMultimoduleProjectDirectory(testExecutionDir));

            final Path mvndHome = Paths.get(Objects.requireNonNull(
                            System.getProperty("mvnd.home"), "System property mvnd.home must be set"))
                    .normalize()
                    .toAbsolutePath();
            if (!Files.isDirectory(mvndHome)) {
                throw new IllegalStateException(
                        "The value of mvnd.home system property points at a path that does not exist or is not a directory: "
                                + mvndHome);
            }
            final Path mvndPropertiesPath = testDir.resolve("mvnd.properties");

            final Path localMavenRepository = deleteDir(testDir.resolve("local-maven-repo"));
            String mrmRepoUrl = System.getProperty("mrm.repository.url");
            if ("".equals(mrmRepoUrl)) {
                mrmRepoUrl = null;
            }
            final Path settingsPath;
            if (mrmRepoUrl == null) {
                LOG.info("Building without mrm-maven-plugin");
                settingsPath = null;
                prefillLocalRepo(localMavenRepository);
            } else {
                LOG.info("Building with mrm-maven-plugin");
                settingsPath = createSettings(testDir.resolve("settings.xml"), mrmRepoUrl);
            }
            final Path home = deleteDir(testDir.resolve("home"));
            final TestParameters parameters = new TestParameters(
                    testDir,
                    mvndPropertiesPath,
                    mvndHome,
                    home,
                    testExecutionDir,
                    multiModuleProjectDirectory,
                    Paths.get(System.getProperty("java.home")).toAbsolutePath().normalize(),
                    localMavenRepository,
                    settingsPath,
                    TimeUtils.toDuration(Environment.MVND_IDLE_TIMEOUT.getDefault()),
                    keepAlive != null && !keepAlive.isEmpty()
                            ? TimeUtils.toDuration(keepAlive)
                            : TimeUtils.toDuration(Environment.MVND_KEEP_ALIVE.getDefault())
                                    .multipliedBy(10),
                    maxLostKeepAlive != null && !maxLostKeepAlive.isEmpty()
                            ? Integer.parseInt(maxLostKeepAlive)
                            : Integer.parseInt(Environment.MVND_MAX_LOST_KEEP_ALIVE.getDefault()) * 10,
                    TEST_MIN_THREADS,
                    true);
            final TestRegistry registry = new TestRegistry(parameters.registry());

            return new MvndResource(parameters, registry, isNative, timeoutMs);
        }

        private static void prefillLocalRepo(final Path localMavenRepository) {
            /* Workaround for https://github.com/apache/maven-mvnd/issues/281 */
            final String preinstallArtifacts =
                    System.getProperty("preinstall.artifacts").trim();
            final Path hostLocalMavenRepo = Paths.get(System.getProperty("mvnd.test.hostLocalMavenRepo"));

            Stream.of(preinstallArtifacts.split("[\\s,]+")).forEach(relPath -> {
                final Path src = hostLocalMavenRepo.resolve(relPath);
                if (Files.isDirectory(src)) {
                    try (Stream<Path> files = Files.list(src)) {
                        files.forEach(file -> {
                            final Path dest =
                                    localMavenRepository.resolve(relPath).resolve(file.getFileName());
                            try {
                                Files.createDirectories(dest.getParent());
                                Files.copy(file, dest);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }

        static Path createSettings(Path settingsPath, String mrmRepoUrl) {
            final Path settingsTemplatePath = Paths.get("src/test/resources/settings-template.xml");
            try {
                final String template = Files.readString(settingsTemplatePath);
                final String content = template.replaceAll("@mrm.repository.url@", mrmRepoUrl);
                try {
                    Files.write(settingsPath, content.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    throw new RuntimeException("Could not write " + settingsPath);
                }
            } catch (IOException e) {
                throw new RuntimeException("Could not read " + settingsTemplatePath);
            }
            return settingsPath;
        }

        public MvndResource(TestParameters parameters, TestRegistry registry, boolean isNative, long timeoutMs) {
            super();
            this.parameters = parameters;
            this.registry = registry;
            this.isNative = isNative;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public void close() throws Exception {
            registry.killAll();
        }
    }
}
