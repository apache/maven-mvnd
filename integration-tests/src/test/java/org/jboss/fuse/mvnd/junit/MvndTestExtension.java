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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jboss.fuse.mvnd.client.BuildProperties;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.DaemonInfo;
import org.jboss.fuse.mvnd.client.DaemonRegistry;
import org.jboss.fuse.mvnd.client.DefaultClient;
import org.jboss.fuse.mvnd.client.Layout;
import org.jboss.fuse.mvnd.jpm.ProcessImpl;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

public class MvndTestExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {

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
            final MvndTest mnvdTest = testClass.getAnnotation(MvndTest.class);
            if (mnvdTest != null) {
                store.put(MvndResource.class.getName(),
                        MvndResource.create(
                                context.getRequiredTestClass().getSimpleName(),
                                mnvdTest.projectDir(),
                                false,
                                -1L));
            } else {
                final MvndNativeTest mvndNativeTest = testClass.getAnnotation(MvndNativeTest.class);
                store.put(MvndResource.class.getName(),
                        MvndResource.create(
                                context.getRequiredTestClass().getSimpleName(),
                                mvndNativeTest.projectDir(),
                                true,
                                mvndNativeTest.timeoutSec() * 1000L));
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
                    if (f.getType() == DaemonRegistry.class) {
                        f.set(testInstance, resource.registry);
                    } else if (Layout.class.isAssignableFrom(f.getType())) {
                        f.set(testInstance, resource.layout);
                    } else if (f.getType() == Client.class) {
                        if (resource.isNative) {
                            final Path mvndNativeExecutablePath = Paths.get(System.getProperty("mvnd.native.executable"))
                                    .toAbsolutePath().normalize();
                            if (!Files.isRegularFile(mvndNativeExecutablePath)) {
                                throw new IllegalStateException("mvnd executable does not exist: " + mvndNativeExecutablePath);
                            }
                            f.set(testInstance,
                                    new NativeTestClient(resource.layout, mvndNativeExecutablePath, resource.timeoutMs));
                        } else {
                            f.set(testInstance, new DefaultClient(() -> resource.layout, BuildProperties.getInstance()));
                        }
                    } else if (f.getType() == NativeTestClient.class) {
                    }
                }
            }
            c = c.getSuperclass();
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

        private final TestLayout layout;
        private final DaemonRegistry registry;
        private final boolean isNative;
        private final long timeoutMs;

        public static MvndResource create(String className, String rawProjectDir, boolean isNative, long timeoutMs)
                throws IOException {
            if (rawProjectDir == null) {
                throw new IllegalStateException("rawProjectDir of @MvndTest must be set");
            }
            final Path testDir = Paths.get("target/mvnd-tests/" + className).toAbsolutePath();
            Files.createDirectories(testDir);
            final Path testExecutionDir;
            if (TEMP_EXTERNAL.equals(rawProjectDir)) {
                try {
                    testExecutionDir = Files.createTempDirectory(MvndTestExtension.class.getSimpleName());
                } catch (IOException e) {
                    throw new RuntimeException("Could not create temporary directory", e);
                }
            } else {
                final Path mvndTestSrcDir = Paths.get(rawProjectDir).toAbsolutePath().normalize();
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
            }

            final Path mvndHome = Paths
                    .get(Objects.requireNonNull(System.getProperty("mvnd.home"), "System property mvnd.home must be set"))
                    .normalize().toAbsolutePath();
            if (!Files.isDirectory(mvndHome)) {
                throw new IllegalStateException(
                        "The value of mvnd.home system property points at a path that does not exist or is not a directory");
            }
            final Path mvndPropertiesPath = testDir.resolve("mvnd.properties");
            final Path localMavenRepository = deleteDir(testDir.resolve("local-maven-repo"));
            final Path settingsPath = createSettings(testDir.resolve("settings.xml"));
            final TestLayout layout = new TestLayout(
                    testDir,
                    mvndPropertiesPath,
                    mvndHome,
                    testExecutionDir,
                    testExecutionDir,
                    Paths.get(System.getProperty("java.home")).toAbsolutePath().normalize(),
                    localMavenRepository, settingsPath,
                    mvndHome.resolve("conf/logging/logback.xml"));
            final DaemonRegistry registry = new DaemonRegistry(layout.registry());

            return new MvndResource(layout, registry, isNative, timeoutMs);
        }

        static Path deleteDir(Path dir) {
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

        static Path createSettings(Path settingsPath) {
            final Path settingsTemplatePath = Paths.get("src/test/resources/settings-template.xml");
            try {
                final String template = new String(Files.readAllBytes(settingsTemplatePath), StandardCharsets.UTF_8);
                final String content = template;
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

        public MvndResource(TestLayout layout, DaemonRegistry registry, boolean isNative, long timeoutMs) {
            super();
            this.layout = layout;
            this.registry = registry;
            this.isNative = isNative;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public void close() throws Exception {
            List<DaemonInfo> daemons;
            final int timeout = 5000;
            final long deadline = System.currentTimeMillis() + timeout;
            while (!(daemons = registry.getAll()).isEmpty()) {
                for (DaemonInfo di : daemons) {
                    try {
                        new ProcessImpl(di.getPid()).destroy();
                    } catch (IOException t) {
                        System.out.println("Daemon " + di.getUid() + ": " + t.getMessage());
                    } catch (Exception t) {
                        System.out.println("Daemon " + di.getUid() + ": " + t);
                    } finally {
                        registry.remove(di.getUid());
                    }
                }
                if (deadline < System.currentTimeMillis() && !registry.getAll().isEmpty()) {
                    throw new RuntimeException("Could not stop all mvnd daemons within " + timeout + " ms");
                }
            }
        }

    }

}
