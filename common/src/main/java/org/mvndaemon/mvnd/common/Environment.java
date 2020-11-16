/*
 * Copyright 2020 the original author or authors.
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
import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Collects system properties and environment variables used by mvnd client or server.
 *
 * Duration properties such as {@link #MVND_IDLE_TIMEOUT}, {@link #MVND_KEEP_ALIVE},
 * {@link #MVND_EXPIRATION_CHECK_DELAY} or {@link #MVND_LOG_PURGE_PERIOD} are expressed
 * in a human readable format such as {@code 2h30m}, {@code 600ms} or {@code 10 seconds}.
 * The available units are <i>d/day/days</i>, <i>h/hour/hours</i>, <i>m/min/minute/minutes</i>,
 * <i>s/sec/second/seconds</i> and <i>ms/millis/msec/milliseconds</i>.
 */
public enum Environment {

    //
    // Log properties
    //

    /**
     * The location of the logback configuration file
     */
    LOGBACK_CONFIGURATION_FILE("logback.configurationFile", null, null, false),

    //
    // System properties
    //
    /** java home directory */
    JAVA_HOME("java.home", "JAVA_HOME", null, false),
    /** mvnd home directory */
    MVND_HOME("mvnd.home", "MVND_HOME", null, false),
    /** user home directory */
    USER_HOME("user.home", null, null, false),
    /** user current dir */
    USER_DIR("user.dir", null, null, false),

    //
    // Maven properties
    //
    /** path to the maven local repository */
    MAVEN_REPO_LOCAL("maven.repo.local", null, null, false),
    /** location of the maven settings file */
    MAVEN_SETTINGS("maven.settings", null, null, false, new String[] { "--settings", "-s" }),
    /** root directory of a multi module project */
    MAVEN_MULTIMODULE_PROJECT_DIRECTORY("maven.multiModuleProjectDirectory", null, null, false),

    //
    // mvnd properties
    //

    /**
     * Location of the user supplied mvnd properties
     */
    MVND_PROPERTIES_PATH("mvnd.propertiesPath", "MVND_PROPERTIES_PATH", null, false),
    /**
     * Directory where mvnd stores its files (the registry and the daemon logs).
     */
    MVND_DAEMON_STORAGE("mvnd.daemonStorage", null, null, false),
    /**
     * The path to the daemon registry, defaults to <code>${mvnd.daemonStorage}/registry.bin</code>
     */
    MVND_REGISTRY("mvnd.registry", null, null, false),
    /**
     * Property that can be set to avoid buffering the output and display events continuously, closer to the usual maven
     * display. Passing {@code -B} or {@code --batch-mode} on the command line enables this too for the given build.
     */
    MVND_NO_BUFERING("mvnd.noBuffering", null, "false", false),
    /**
     * The number of log lines to display for each Maven module that is built in parallel.
     */
    MVND_ROLLING_WINDOW_SIZE("mvnd.rollingWindowSize", null, "0", false),
    /**
     * The automatic log purge period.
     */
    MVND_LOG_PURGE_PERIOD("mvnd.logPurgePeriod", null, "7d", false, true),
    /**
     * Property to disable using a daemon (usefull for debugging, and only available in non native mode).
     */
    MVND_NO_DAEMON("mvnd.noDaemon", "MVND_NO_DAEMON", "false", true),
    /**
     * Property to launch the daemon in debug mode with the following JVM argument
     * <code>-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000</code>
     */
    MVND_DEBUG("mvnd.debug", null, false, true),
    /**
     * Duration after which an usused daemon will shut down.
     */
    MVND_IDLE_TIMEOUT("mvnd.idleTimeout", null, "3 hours", true, true),
    /**
     * Time after which a daemon will send a keep-alive message to the client if the current build
     * has produced no output.
     */
    MVND_KEEP_ALIVE("mvnd.keepAlive", null, "100 ms", true, true),
    /**
     * The maximum number of keep alive message that can be lost before the client considers the daemon
     * as having had a failure.
     */
    MVND_MAX_LOST_KEEP_ALIVE("mvnd.maxLostKeepAlive", null, 30, false),
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
    MVND_THREADS("mvnd.threads", null, null, false, new String[] { "--threads", "-T" }),
    /**
     * The maven builder name to use. Ignored if the user passes
     *
     * {@code -b} or {@code --builder} on the command line
     */
    MVND_BUILDER("mvnd.builder", null, "smart", false, new String[] { "--builder", "-b" }),
    /**
     * Internal system property set by the client when starting the daemon to identify its id
     */
    MVND_UID("mvnd.uid", null, null, false),
    /**
     * Internal option to specify the maven extension classpath
     */
    MVND_EXT_CLASSPATH("mvnd.extClasspath", null, null, true),
    /**
     * Internal option to specify the list of maven extension to register
     */
    MVND_CORE_EXTENSIONS("mvnd.coreExtensions", null, null, true),
    /**
     * JVM options for the daemon
     */
    MVND_MIN_HEAP_SIZE("mvnd.minHeapSize", null, "128M", true),
    /**
     * JVM options for the daemon
     */
    MVND_MAX_HEAP_SIZE("mvnd.maxHeapSize", null, "2G", true),
    /**
     * Additional JVM args for the daemon
     */
    MVND_JVM_ARGS("mvnd.jvmArgs", null, "", true),
    /**
     * JVM options for the daemon
     */
    MVND_ENABLE_ASSERTIONS("mvnd.enableAssertions", null, false, true),
    /**
     * Interval to check if the daemon should expire
     */
    MVND_EXPIRATION_CHECK_DELAY("mvnd.expirationCheckDelay", null, "10 seconds", true, true),
    /**
     * Period after which idle daemons will shut down
     */
    MVND_DUPLICATE_DAEMON_GRACE_PERIOD("mvnd.duplicateDaemonGracePeriod", null, "10 seconds", true, true),
    ;

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
    private final boolean duration;
    private final String[] options;

    Environment(String property, String environmentVariable, Object def, boolean discriminating) {
        this(property, environmentVariable, def, discriminating, false, null);
    }

    Environment(String property, String environmentVariable, Object def, boolean discriminating, boolean duration) {
        this(property, environmentVariable, def, discriminating, duration, null);
    }

    Environment(String property, String environmentVariable, Object def, boolean discriminating, String[] options) {
        this(property, environmentVariable, def, discriminating, false, options);
    }

    Environment(String property, String environmentVariable, Object def, boolean discriminating, boolean duration,
            String[] options) {
        this.property = Objects.requireNonNull(property);
        this.environmentVariable = environmentVariable;
        this.def = def != null ? def.toString() : null;
        this.discriminating = discriminating;
        this.duration = duration;
        this.options = options;
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

    public Duration asDuration() {
        return TimeUtils.toDuration(asString());
    }

    protected String prepareValue(String value) {
        // For durations, we need to make sure spaces are removed, so reformat the value
        return duration ? TimeUtils.printDuration(TimeUtils.toMilliSeconds(value)) : value;
    }

    public String asDaemonOpt(String value) {
        return property + "=" + prepareValue(value);
    }

    public String asCommandLineProperty(String value) {
        return (options != null ? options[0] : "-D" + property) + "=" + prepareValue(value);
    }

    public boolean hasCommandLineProperty(Collection<String> args) {
        final String[] prefixes = options != null ? options : new String[] { "-D" + property + "=" };
        return args.stream().anyMatch(arg -> Stream.of(prefixes).anyMatch(arg::startsWith));
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
