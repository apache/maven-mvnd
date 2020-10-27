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
package org.jboss.fuse.mvnd.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects system properties and environment variables used by mvnd client or server.
 */
public enum Environment {
    LOGBACK_CONFIGURATION_FILE("logback.configurationFile", null),
    JAVA_HOME("java.home", "JAVA_HOME"),
    MVND_HOME("mvnd.home", "MVND_HOME"),
    MAVEN_REPO_LOCAL("maven.repo.local", null),
    MAVEN_MULTIMODULE_PROJECT_DIRECTORY("maven.multiModuleProjectDirectory", null),
    MVND_PROPERTIES_PATH("mvnd.properties.path", "MVND_PROPERTIES_PATH"),
    DAEMON_DEBUG("daemon.debug", null),
    DAEMON_IDLE_TIMEOUT_MS("daemon.idleTimeoutMs", null),
    DAEMON_KEEP_ALIVE_MS("daemon.keepAliveMs", null),
    DAEMON_MAX_LOST_KEEP_ALIVE("daemon.maxLostKeepAlive", null),
    MVND_MIN_THREADS("mvnd.minThreads", null),
    DAEMON_UID("daemon.uid", null);

    public static final int DEFAULT_IDLE_TIMEOUT = (int) TimeUnit.HOURS.toMillis(3);

    public static final int DEFAULT_KEEP_ALIVE = (int) TimeUnit.SECONDS.toMillis(1);

    public static final int DEFAULT_MAX_LOST_KEEP_ALIVE = 3;

    public static final int DEFAULT_MIN_THREADS = 1;

    private static final Consumer<String> LOG;
    private static final boolean DEBUG_ENABLED;
    public static final String DEBUG_ENVIRONMENT_PROP = "mvnd.environment.debug";

    static {
        Consumer<String> log = null;
        boolean debugEnabled = false;
        try {
            Logger logger = LoggerFactory.getLogger(Environment.class);
            log = logger::debug;
            debugEnabled = logger.isDebugEnabled();
        } catch (java.lang.NoClassDefFoundError e) {
            if (e.getMessage().contains("org/slf4j/LoggerFactory")) {
                /* This is when we are in the daemon's boot class path where slf4j is not available */
                if (Boolean.getBoolean(DEBUG_ENVIRONMENT_PROP)) {
                    log = s -> System.out.println("mvnd.environment: " + s);
                    debugEnabled = true;
                }
            } else {
                throw e;
            }
        }
        LOG = log != null ? log : s -> {
        };
        DEBUG_ENABLED = debugEnabled;
    }

    static Properties properties = System.getProperties();
    static Map<String, String> env = System.getenv();

    private final String property;
    private final String environmentVariable;

    Environment(String property, String environmentVariable) {
        this.property = property;
        this.environmentVariable = environmentVariable;
    }

    public Environment.EnvValue systemProperty() {
        return new EnvValue(this, systemPropertySource());
    }

    public Environment.EnvValue commandLineProperty(Supplier<Properties> commandLineProperties) {
        return new EnvValue(this, new ValueSource(
                description -> description.append("command line property ").append(property),
                () -> commandLineProperties.get().getProperty(property)));
    }

    public Environment.EnvValue environmentVariable() {
        return new EnvValue(this, environmentVariableSource());
    }

    public EnvValue fromValueSource(ValueSource valueSource) {
        return new EnvValue(this, valueSource);
    }

    public String asCommandLineProperty(String value) {
        return "-D" + property + "=" + value;
    }

    public boolean hasCommandLineProperty(Collection<String> args) {
        final String prefix = "-D" + property + "=";
        return args.stream().anyMatch(s -> s.startsWith(prefix));
    }

    public static Path findJavaHome(Supplier<Properties> mvndProperties, Path mvndPropertiesPath) {
        final Path result = JAVA_HOME
                .environmentVariable()
                .orLocalProperty(mvndProperties, mvndPropertiesPath)
                .orSystemProperty()
                .orFail()
                .asPath();
        try {
            return result
                    .toRealPath();
        } catch (IOException e) {
            throw new RuntimeException("Could not get a real path from path " + result);
        }
    }

    public static Path findMvndPropertiesPath() {
        return MVND_PROPERTIES_PATH
                .environmentVariable()
                .orSystemProperty()
                .orDefault(() -> System.getProperty("user.home") + "/.m2/mvnd.properties")
                .asPath()
                .toAbsolutePath().normalize();
    }

    public static EnvValue findBasicMavenHome() {
        return MVND_HOME
                .environmentVariable()
                .orSystemProperty();
    }

    public static Path findMultiModuleProjectDirectory(Path pwd) {
        return MAVEN_MULTIMODULE_PROJECT_DIRECTORY
                .systemProperty()
                .orDefault(() -> findDefaultMultimoduleProjectDirectory(pwd))
                .asPath()
                .toAbsolutePath().normalize();
    }

    public static String findDefaultMultimoduleProjectDirectory(Path pwd) {
        Path dir = pwd;
        do {
            if (Files.isDirectory(dir.resolve(".mvn"))) {
                return dir.toString();
            }
            dir = dir.getParent();
        } while (dir != null);
        /*
         * Return pwd if .mvn directory was not found in the hierarchy.
         * Maven does the same thing in mvn shell script's find_maven_basedir()
         * and find_file_argument_basedir() routines
         */
        return pwd.toString();
    }

    public static Path findLogbackConfigurationPath(Supplier<Properties> mvndProperties, Path mvndPropertiesPath,
            Path mvndHome) {
        return LOGBACK_CONFIGURATION_FILE
                .systemProperty()
                .orLocalProperty(mvndProperties, mvndPropertiesPath)
                .orDefault(() -> mvndHome.resolve("mvn/conf/logging/logback.xml").toString())
                .orFail()
                .asPath();
    }

    public static boolean isNative() {
        return "executable".equals(System.getProperty("org.graalvm.nativeimage.kind"));
    }

    private Environment.ValueSource systemPropertySource() {
        if (property == null) {
            throw new IllegalStateException("Cannot use " + Environment.class.getName() + " for getting a system property");
        }
        return new ValueSource(
                description -> description.append("system property ").append(property),
                () -> properties.getProperty(property));
    }

    private Environment.ValueSource environmentVariableSource() {
        if (environmentVariable == null) {
            throw new IllegalStateException(
                    "Cannot use " + Environment.class.getName() + "." + name() + " for getting an environment variable");
        }
        return new ValueSource(
                description -> description.append("environment variable ").append(environmentVariable),
                () -> env.get(environmentVariable));
    }

    /**
     * A source of an environment value with a description capability.
     */
    public static class ValueSource {
        final Function<StringBuilder, StringBuilder> descriptionFunction;
        final Supplier<String> valueSupplier;

        public ValueSource(Function<StringBuilder, StringBuilder> descriptionFunction, Supplier<String> valueSupplier) {
            this.descriptionFunction = descriptionFunction;
            this.valueSupplier = valueSupplier;
        }

        /** Mostly for debugging */
        @Override
        public String toString() {
            return descriptionFunction.apply(new StringBuilder()).toString();
        }

    }

    /**
     * A chained lazy environment value.
     */
    public static class EnvValue {
        private final Environment envKey;
        private final Environment.ValueSource valueSource;
        protected Environment.EnvValue previous;

        public EnvValue(Environment envKey, Environment.ValueSource valueSource) {
            this.previous = null;
            this.envKey = envKey;
            this.valueSource = valueSource;
        }

        public EnvValue(Environment.EnvValue previous, Environment envKey, Environment.ValueSource valueSource) {
            this.previous = previous;
            this.envKey = envKey;
            this.valueSource = valueSource;
        }

        public Environment.EnvValue orSystemProperty() {
            return new EnvValue(this, envKey, envKey.systemPropertySource());
        }

        public Environment.EnvValue orLocalProperty(Supplier<Properties> localProperties, Path localPropertiesPath) {
            return new EnvValue(this, envKey, new ValueSource(
                    description -> description.append("property ").append(envKey.property).append(" in ")
                            .append(localPropertiesPath),
                    () -> localProperties.get().getProperty(envKey.property)));
        }

        public Environment.EnvValue orEnvironmentVariable() {
            return new EnvValue(this, envKey, envKey.environmentVariableSource());
        }

        public EnvValue or(ValueSource source) {
            return new EnvValue(this, envKey, source);
        }

        public Environment.EnvValue orDefault(Supplier<String> defaultSupplier) {
            return new EnvValue(this, envKey,
                    new ValueSource(sb -> sb.append("default: ").append(defaultSupplier.get()), defaultSupplier));
        }

        public Environment.EnvValue orFail() {
            return new EnvValue(this, envKey, new ValueSource(sb -> sb, () -> {
                final StringBuilder sb = new StringBuilder("Could not get value for ")
                        .append(Environment.class.getSimpleName())
                        .append(".").append(envKey.name()).append(" from any of the following sources: ");

                /*
                 * Compose the description functions to invert the order thus getting the resolution order in the
                 * message
                 */
                Function<StringBuilder, StringBuilder> description = (s -> s);
                EnvValue val = this;
                while (val != null) {
                    description = description.compose(val.valueSource.descriptionFunction);
                    val = val.previous;
                    if (val != null) {
                        description = description.compose(s -> s.append(", "));
                    }
                }
                description.apply(sb);
                throw new IllegalStateException(sb.toString());
            }));
        }

        String get() {
            if (previous != null) {
                final String result = previous.get();
                if (result != null) {
                    return result;
                }
            }
            final String result = valueSource.valueSupplier.get();
            if (result != null && DEBUG_ENABLED) {
                StringBuilder sb = new StringBuilder("Loaded environment value for key [")
                        .append(envKey.name())
                        .append("] from ");
                valueSource.descriptionFunction.apply(sb);
                sb.append(": [")
                        .append(result)
                        .append(']');
                LOG.accept(sb.toString());
            }
            return result;
        }

        public String asString() {
            return get();
        }

        public Optional<String> asOptional() {
            return Optional.ofNullable(get());
        }

        public Path asPath() {
            final String result = get();
            return result == null ? null : Paths.get(result);
        }

        public boolean asBoolean() {
            return Boolean.parseBoolean(get());
        }

        public int asInt() {
            return Integer.parseInt(get());
        }

    }

}
