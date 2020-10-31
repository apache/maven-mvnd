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
    private final int idleTimeoutMs;
    private final int keepAliveMs;
    private final int maxLostKeepAlive;
    private final String threads;

    public static ClientLayout getEnvInstance() {
        if (ENV_INSTANCE == null) {
            final Path mvndPropertiesPath = Environment.findMvndPropertiesPath();
            final Supplier<Properties> mvndProperties = lazyMvndProperties(mvndPropertiesPath);
            final Path pwd = Paths.get(".").toAbsolutePath().normalize();
            final Path mvndHome = Environment.MVND_HOME
                    .fromValueSource(new ValueSource(
                            description -> description.append("path relative to the mvnd executable"),
                            () -> {
                                Optional<String> cmd = ProcessHandle.current().info().command();
                                if (Environment.isNative() && cmd.isPresent()) {
                                    final Path mvndH = Paths.get(cmd.get()).getParent().getParent();
                                    if (mvndH != null) {
                                        final Path mvndDaemonLib = mvndH
                                                .resolve("mvn/lib/ext/mvnd-daemon-" + BuildProperties.getInstance().getVersion()
                                                        + ".jar");
                                        if (Files.exists(mvndDaemonLib)) {
                                            return mvndH.toString();
                                        }
                                    }
                                }
                                return null;
                            }))
                    .orSystemProperty()
                    .orEnvironmentVariable()
                    .orLocalProperty(mvndProperties, mvndPropertiesPath)
                    .orFail()
                    .asPath()
                    .toAbsolutePath().normalize();
            final int idleTimeoutMs = Environment.DAEMON_IDLE_TIMEOUT_MS
                    .systemProperty()
                    .orLocalProperty(mvndProperties, mvndPropertiesPath)
                    .orDefault(() -> Integer.toString(Environment.DEFAULT_IDLE_TIMEOUT))
                    .asInt();
            final int keepAliveMs = Environment.DAEMON_KEEP_ALIVE_MS
                    .systemProperty()
                    .orLocalProperty(mvndProperties, mvndPropertiesPath)
                    .orDefault(() -> Integer.toString(Environment.DEFAULT_KEEP_ALIVE))
                    .asInt();
            final int maxLostKeepAlive = Environment.DAEMON_MAX_LOST_KEEP_ALIVE
                    .systemProperty()
                    .orLocalProperty(mvndProperties, mvndPropertiesPath)
                    .orDefault(() -> Integer.toString(Environment.DEFAULT_MAX_LOST_KEEP_ALIVE))
                    .asInt();
            final String threads = Environment.MVND_THREADS
                    .systemProperty()
                    .orLocalProperty(mvndProperties, mvndPropertiesPath)
                    .orDefault(() -> {
                        final int minThreads = Environment.MVND_MIN_THREADS
                                .systemProperty()
                                .orLocalProperty(mvndProperties, mvndPropertiesPath)
                                .orDefault(() -> Integer.toString(Environment.DEFAULT_MIN_THREADS))
                                .asInt();
                        return String
                                .valueOf(Math.max(Runtime.getRuntime().availableProcessors() - 1, minThreads));
                    })
                    .asString();

            ENV_INSTANCE = new ClientLayout(
                    mvndPropertiesPath,
                    mvndHome,
                    pwd,
                    Environment.findMultiModuleProjectDirectory(pwd),
                    Environment.findJavaHome(mvndProperties, mvndPropertiesPath),
                    findLocalRepo(),
                    null,
                    Environment.findLogbackConfigurationPath(mvndProperties, mvndPropertiesPath, mvndHome),
                    idleTimeoutMs, keepAliveMs, maxLostKeepAlive, threads);
        }
        return ENV_INSTANCE;
    }

    public ClientLayout(Path mvndPropertiesPath, Path mavenHome, Path userDir, Path multiModuleProjectDirectory, Path javaHome,
            Path localMavenRepository, Path settings, Path logbackConfigurationPath, int idleTimeoutMs, int keepAliveMs,
            int maxLostKeepAlive, String threads) {
        super(mvndPropertiesPath, mavenHome, userDir, multiModuleProjectDirectory);
        this.localMavenRepository = localMavenRepository;
        this.settings = settings;
        this.javaHome = Objects.requireNonNull(javaHome, "javaHome");
        this.logbackConfigurationPath = logbackConfigurationPath;
        this.idleTimeoutMs = idleTimeoutMs;
        this.keepAliveMs = keepAliveMs;
        this.maxLostKeepAlive = maxLostKeepAlive;
        this.threads = threads;
    }

    /**
     * @param  newUserDir where to change the current directory to
     * @return            a new {@link ClientLayout} with {@code userDir} set to the given {@code newUserDir}
     */
    public ClientLayout cd(Path newUserDir) {
        return new ClientLayout(mvndPropertiesPath, mavenHome, newUserDir, multiModuleProjectDirectory, javaHome,
                localMavenRepository, settings, logbackConfigurationPath, idleTimeoutMs, keepAliveMs, maxLostKeepAlive,
                threads);
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

    public int getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    public int getKeepAliveMs() {
        return keepAliveMs;
    }

    public int getMaxLostKeepAlive() {
        return maxLostKeepAlive;
    }

    /**
     * @return the number of threads (same syntax as Maven's {@code -T}/{@code --threads} option) to pass to the daemon
     *         unless the user passes his own `-T` or `--threads`.
     */
    public String getThreads() {
        return threads;
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
