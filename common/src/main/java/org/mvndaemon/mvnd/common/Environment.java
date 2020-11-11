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
package org.mvndaemon.mvnd.common;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Collects system properties and environment variables used by mvnd client or server.
 */
public enum Environment {
    //
    // Log properties
    //
    LOGBACK_CONFIGURATION_FILE("logback.configurationFile", null, null, false),
    //
    // System properties
    //
    JAVA_HOME("java.home", "JAVA_HOME", null, false),
    MVND_HOME("mvnd.home", "MVND_HOME", null, false),
    USER_HOME("user.home", null, null, false),
    USER_DIR("user.dir", null, null, false),
    //
    // Maven properties
    //
    MAVEN_REPO_LOCAL("maven.repo.local", null, null, false),
    MAVEN_SETTINGS("maven.settings", null, null, false) {
        @Override
        public boolean hasCommandLineProperty(Collection<String> args) {
            return args.stream().anyMatch(arg -> arg.startsWith("-s") || arg.startsWith("--settings"));
        }

        @Override
        public String asCommandLineProperty(String value) {
            return "--settings=" + value;
        }
    },
    MAVEN_MULTIMODULE_PROJECT_DIRECTORY("maven.multiModuleProjectDirectory", null, null, false),
    //
    // mvnd properties
    //
    MVND_PROPERTIES_PATH("mvnd.properties.path", "MVND_PROPERTIES_PATH", null, false),
    MVND_DAEMON_STORAGE("mvnd.daemon.storage", null, null, false),
    /**
     * Property that can be set to avoid buffering the output and display events continuously, closer to the usual maven
     * display.
     */
    MVND_NO_BUFERING("mvnd.noBuffering", null, "false", false),
    /**
     * The size of the rolling window
     */
    MVND_ROLLING_WINDOW_SIZE("mvnd.rollingWindowSize", null, "2", false),
    /**
     * The path to the daemon registry
     */
    DAEMON_REGISTRY("daemon.registry", null, null, false),
    MVND_NO_DAEMON("mvnd.noDaemon", "MVND_NO_DAEMON", "false", true),
    DAEMON_DEBUG("daemon.debug", null, false, true),
    DAEMON_IDLE_TIMEOUT_MS("daemon.idleTimeoutMs", null, TimeUnit.HOURS.toMillis(3), true),
    DAEMON_KEEP_ALIVE_MS("daemon.keepAliveMs", null, TimeUnit.SECONDS.toMillis(1), true),
    DAEMON_MAX_LOST_KEEP_ALIVE("daemon.maxLostKeepAlive", null, 3, false),
    /**
     * The minimum number of threads to use when constructing the default {@code -T} parameter for the daemon.
     * This value is ignored if the user passes @{@code -T}, @{@code --threads} or {@code -Dmvnd.threads} on the command
     * line or if he sets {@code mvnd.threads} in {@code ~/.m2/mvnd.properties}.
     */
    MVND_MIN_THREADS("mvnd.minThreads", null, 1, false),
    /**
     * The number of threads to pass to the daemon; same syntax as Maven's {@code -T}/{@code --threads} option. Ignored
     * if the user passes @{@code -T}, @{@code --threads} or {@code -Dmvnd.threads} on the command
     * line.
     */
    MVND_THREADS("mvnd.threads", null, null, false) {
        @Override
        public boolean hasCommandLineProperty(Collection<String> args) {
            return args.stream().anyMatch(arg -> arg.startsWith("-T") || arg.startsWith("--threads"));
        }

        @Override
        public String asCommandLineProperty(String value) {
            return "--threads=" + value;
        }
    },
    /**
     * The maven builder name to use. Ignored if the user passes
     * 
     * @{@code -b} or @{@code --builder} on the command line
     */
    MVND_BUILDER("mvnd.builder", null, "smart", false) {
        @Override
        public boolean hasCommandLineProperty(Collection<String> args) {
            return args.stream().anyMatch(arg -> arg.startsWith("-b") || arg.startsWith("--builder"));
        }

        @Override
        public String asCommandLineProperty(String value) {
            return "--builder=" + value;
        }
    },
    /**
     * Internal system property set by the client when starting the daemon to identify its id
     */
    DAEMON_UID("daemon.uid", null, null, false),
    /**
     * Internal option to specify the maven extension classpath
     */
    DAEMON_EXT_CLASSPATH("daemon.ext.classpath", null, null, true),
    /**
     * Internal option to specify the list of maven extension to register
     */
    DAEMON_CORE_EXTENSIONS("daemon.core.extensions", null, null, true),
    /**
     * JVM options for the daemon
     */
    DAEMON_MIN_HEAP_SIZE("daemon.minHeapSize", null, "128M", true),
    /**
     * JVM options for the daemon
     */
    DAEMON_MAX_HEAP_SIZE("daemon.maxHeapSize", null, "2G", true),
    /**
     * Additional JVM args for the daemon
     */
    DAEMON_JVM_ARGS("daemon.jvmArgs", null, "", true),
    /**
     * JVM options for the daemon
     */
    DAEMON_ENABLE_ASSERTIONS("daemon.enableAssertions", null, false, true),
    /**
     * Interval to check if the daemon should expire
     */
    DAEMON_EXPIRATION_CHECK_DELAY_MS("daemon.expirationCheckDelayMs", null, TimeUnit.SECONDS.toMillis(10), true),
    /**
     * Period after which idle daemons will shut down
     */
    DAEMON_DUPLICATE_DAEMON_GRACE_PERIOD_MS("daemon.duplicateDaemonGracePeriodMs", null, TimeUnit.SECONDS.toMillis(10), true),
    ;

    public static final int DEFAULT_IDLE_TIMEOUT = (int) TimeUnit.HOURS.toMillis(3);

    public static final int DEFAULT_KEEP_ALIVE = (int) TimeUnit.SECONDS.toMillis(1);

    static Properties properties = System.getProperties();

    public static void setProperties(Properties properties) {
        Environment.properties = properties;
    }

    public static String getProperty(String property) {
        return properties.getProperty(property);
    }

    private final String property;
    private final String environmentVariable;
    private final String def;
    private final boolean discriminating;

    Environment(String property, String environmentVariable, Object def, boolean discriminating) {
        this.property = Objects.requireNonNull(property);
        this.environmentVariable = environmentVariable;
        this.def = def != null ? def.toString() : null;
        this.discriminating = discriminating;
    }

    public String getProperty() {
        return property;
    }

    public String getEnvironmentVariable() {
        return environmentVariable;
    }

    public String getDef() {
        return def;
    }

    public boolean isDiscriminating() {
        return discriminating;
    }

    public String asString() {
        String val = getProperty(property);
        if (val == null) {
            throw new IllegalStateException("The system property " + property + " is missing");
        }
        return val;
    }

    public int asInt() {
        return Integer.parseInt(asString());
    }

    public boolean asBoolean() {
        return Boolean.parseBoolean(asString());
    }

    public Path asPath() {
        String result = asString();
        if (Os.current().isCygwin()) {
            result = cygpath(result);
        }
        return Paths.get(result);
    }

    public String asCommandLineProperty(String value) {
        return "-D" + property + "=" + value;
    }

    public String asDaemonOpt(String value) {
        return property + "=" + value;
    }

    public boolean hasCommandLineProperty(Collection<String> args) {
        final String prefix = "-D" + getProperty() + "=";
        return args.stream().anyMatch(s -> s.startsWith(prefix));
    }

    public static String cygpath(String result) {
        String path = result.replace('/', '\\');
        if (path.matches("\\\\cygdrive\\\\[a-z]\\\\.*")) {
            String s = path.substring("\\cygdrive\\".length());
            result = s.substring(0, 1).toUpperCase(Locale.ENGLISH) + ":" + s.substring(1);
        }
        return result;
    }

    public static boolean isNative() {
        return "executable".equals(System.getProperty("org.graalvm.nativeimage.kind"));
    }

}
