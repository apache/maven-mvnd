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
import java.util.Optional;
import java.util.stream.Stream;

import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.ClientLayout;
import org.jboss.fuse.mvnd.client.DaemonInfo;
import org.jboss.fuse.mvnd.client.DaemonRegistry;
import org.jboss.fuse.mvnd.client.Layout;
import org.jboss.fuse.mvnd.jpm.ProcessImpl;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

public class MvndTestExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {

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
            store.put(MvndResource.class.getName(),
                    MvndResource.create(context.getRequiredTestClass().getSimpleName(), mnvdTest.projectDir()));
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
                    } else if (f.getType() == Layout.class) {
                        f.set(testInstance, resource.layout);
                    } else if (f.getType() == Client.class) {
                        f.set(testInstance, new Client(resource.layout, Optional.of(resource.clientLayout)));
                    } else if (f.getType() == ClientLayout.class) {
                        f.set(testInstance, resource.clientLayout);
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

        private final ClientLayout clientLayout;
        private final Layout layout;
        private final DaemonRegistry registry;

        public static MvndResource create(String className, String rawProjectDir) throws IOException {
            if (rawProjectDir == null) {
                throw new IllegalStateException("rawProjectDir of @MvndTest must be set");
            }
            final Path mvndTestSrcDir = Paths.get(rawProjectDir).toAbsolutePath().normalize();
            if (!Files.exists(mvndTestSrcDir)) {
                throw new IllegalStateException("@MvndTest(projectDir = \"" + rawProjectDir
                        + "\") points at a path that does not exist: " + mvndTestSrcDir);
            }

            final Path testDir = Paths.get("target/mvnd-tests/" + className).toAbsolutePath();
            final Path testExecutionDir = testDir.resolve("project");
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

            final Path mvndHome = Paths
                    .get(Objects.requireNonNull(System.getProperty("mvnd.home"), "System property mvnd.home must be set"))
                    .normalize().toAbsolutePath();
            if (!Files.isDirectory(mvndHome)) {
                throw new IllegalStateException(
                        "The value of mvnd.home system property points at a path that does not exist or is not a directory");
            }
            final Layout layout = new Layout(Paths.get(System.getProperty("java.home")).toAbsolutePath().normalize(),
                    mvndHome,
                    testExecutionDir,
                    testExecutionDir);
            final DaemonRegistry registry = new DaemonRegistry(layout.registry());

            final Path localMavenRepository = deleteDir(testDir.resolve("local-maven-repo"));
            final Path settingsPath = createSettings(testDir.resolve("settings.xml"));
            final ClientLayout clientLayout = new ClientLayout(localMavenRepository, settingsPath);
            return new MvndResource(layout, registry, clientLayout);
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

        public MvndResource(Layout layout, DaemonRegistry registry, ClientLayout clientLayout) {
            super();
            this.layout = layout;
            this.registry = registry;
            this.clientLayout = clientLayout;
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
