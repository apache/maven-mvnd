package org.jboss.fuse.mvnd.client;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Local paths relevant for the {@link DefaultClient}.
 */
public class ClientLayout extends Layout {

    private static ClientLayout ENV_INSTANCE;

    private final Path localMavenRepository;
    private final Path settings;
    private final Path javaHome;

    public static ClientLayout getEnvInstance() {
        if (ENV_INSTANCE == null) {
            final Path mvndPropertiesPath = Environment.findMvndPropertiesPath();
            final Supplier<Properties> mvndProperties = lazyMvndProperties(mvndPropertiesPath);
            final Path pwd = Paths.get(".").toAbsolutePath().normalize();
            final Path mvndHome = Environment.findMavenHome(mvndProperties, mvndPropertiesPath);
            ENV_INSTANCE = new ClientLayout(
                    mvndPropertiesPath,
                    mvndHome,
                    pwd,
                    Environment.findMultiModuleProjectDirectory(pwd),
                    Environment.findJavaHome(mvndProperties, mvndPropertiesPath),
                    findLocalRepo(),
                    null);
        }
        return ENV_INSTANCE;
    }

    public ClientLayout(Path mvndPropertiesPath, Path mavenHome, Path userDir, Path multiModuleProjectDirectory, Path javaHome,
            Path localMavenRepository, Path settings) {
        super(mvndPropertiesPath, mavenHome, userDir, multiModuleProjectDirectory);
        this.localMavenRepository = localMavenRepository;
        this.settings = settings;
        this.javaHome = Objects.requireNonNull(javaHome, "javaHome");
    }

    /**
     * @return absolute normalized path to local Maven repository or {@code null} if the server is supposed to use the default
     */
    public Path getLocalMavenRepository() {
        return localMavenRepository;
    }

    /**
     * @return absolute normalized path to {@code settings.xml} or {@code null}
     */
    public Path getSettings() {
        return settings;
    }

    public Path javaHome() {
        return javaHome;
    }

    static Path findLocalRepo() {
        return Environment.MAVEN_REPO_LOCAL.systemProperty().asPath();
    }

    static Path findLogbackConfigurationFile(Supplier<Properties> mvndProperties, Path mvndPropertiesPath, Path mvndHome) {
        final String rawValue = Environment.LOGBACK_CONFIGURATION_FILE
                .systemProperty()
                .orLocalProperty(mvndProperties, mvndPropertiesPath)
                .asString();
        if (rawValue != null) {
            return Paths.get(rawValue).toAbsolutePath().normalize();
        }
        return mvndHome.resolve("conf/logging/logback.xml");
    }

    @Override
    public String toString() {
        return "ClientLayout [localMavenRepository=" + localMavenRepository + ", settings=" + settings + ", javaHome="
                + javaHome + ", mavenHome()=" + mavenHome() + ", userDir()=" + userDir() + ", registry()=" + registry()
                + ", multiModuleProjectDirectory()=" + multiModuleProjectDirectory() + "]";
    }

}
