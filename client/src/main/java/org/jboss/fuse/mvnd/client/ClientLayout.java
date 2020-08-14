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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import org.jboss.fuse.mvnd.common.BuildProperties;
import org.jboss.fuse.mvnd.common.Environment;
import org.jboss.fuse.mvnd.common.Environment.ValueSource;
import org.jboss.fuse.mvnd.common.Layout;

/**
 * Local paths relevant for the {@link DefaultClient}.
 */
public class ClientLayout extends Layout {

    private static ClientLayout ENV_INSTANCE;

    private final Path localMavenRepository;
    private final Path settings;
    private final Path javaHome;
    private final Path logbackConfigurationPath;

    public static ClientLayout getEnvInstance() {
        if (ENV_INSTANCE == null) {
            final Path mvndPropertiesPath = Environment.findMvndPropertiesPath();
            final Supplier<Properties> mvndProperties = lazyMvndProperties(mvndPropertiesPath);
            final Path pwd = Paths.get(".").toAbsolutePath().normalize();
            final Path mvndHome = Environment.findBasicMavenHome()
                    .orLocalProperty(mvndProperties, mvndPropertiesPath)
                    .or(new ValueSource(
                            description -> description.append("path relative to the mvnd executable"),
                            () -> {
                                Optional<String> cmd = ProcessHandle.current().info().command();
                                if (Environment.isNative() && cmd.isPresent()) {
                                    final Path mvndH = Paths.get(cmd.get()).getParent().getParent();
                                    if (mvndH != null) {
                                        final Path mvndDaemonLib = mvndH
                                                .resolve("lib/ext/mvnd-daemon-" + BuildProperties.getInstance().getVersion()
                                                        + ".jar");
                                        if (Files.exists(mvndDaemonLib)) {
                                            return mvndH.toString();
                                        }
                                    }
                                }
                                return null;
                            }))
                    .orFail()
                    .asPath()
                    .toAbsolutePath().normalize();
            ENV_INSTANCE = new ClientLayout(
                    mvndPropertiesPath,
                    mvndHome,
                    pwd,
                    Environment.findMultiModuleProjectDirectory(pwd),
                    Environment.findJavaHome(mvndProperties, mvndPropertiesPath),
                    findLocalRepo(),
                    null,
                    Environment.findLogbackConfigurationPath(mvndProperties, mvndPropertiesPath, mvndHome));
        }
        return ENV_INSTANCE;
    }

    public ClientLayout(Path mvndPropertiesPath, Path mavenHome, Path userDir, Path multiModuleProjectDirectory, Path javaHome,
            Path localMavenRepository, Path settings, Path logbackConfigurationPath) {
        super(mvndPropertiesPath, mavenHome, userDir, multiModuleProjectDirectory);
        this.localMavenRepository = localMavenRepository;
        this.settings = settings;
        this.javaHome = Objects.requireNonNull(javaHome, "javaHome");
        this.logbackConfigurationPath = logbackConfigurationPath;
    }

    /**
     * @return absolute normalized path to local Maven repository or {@code null} if the server is supposed to use the
     *         default
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

    public Path getLogbackConfigurationPath() {
        return logbackConfigurationPath;
    }

    static Path findLocalRepo() {
        return Environment.MAVEN_REPO_LOCAL.systemProperty().asPath();
    }

    @Override
    public String toString() {
        return "ClientLayout [localMavenRepository=" + localMavenRepository + ", settings=" + settings + ", javaHome="
                + javaHome + ", mavenHome()=" + mavenHome() + ", userDir()=" + userDir() + ", registry()=" + registry()
                + ", multiModuleProjectDirectory()=" + multiModuleProjectDirectory() + "]";
    }

}
