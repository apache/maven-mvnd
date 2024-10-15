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
package org.mvndaemon.mvnd.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Collects system properties and environment variables used by mvnd client or server.
 *
 * Duration properties such as {@link #MVND_IDLE_TIMEOUT}, {@link #MVND_KEEP_ALIVE},
 * {@link #MVND_EXPIRATION_CHECK_DELAY} or {@link #MVND_LOG_PURGE_PERIOD} are expressed
 * in a human readable format such as <code>2h30m</code>, <code>600ms</code> or <code>10 seconds</code>.
 * The available units are <i>d/day/days</i>, <i>h/hour/hours</i>, <i>m/min/minute/minutes</i>,
 * <i>s/sec/second/seconds</i> and <i>ms/millis/msec/milliseconds</i>.
 */
public enum Environment {

    /**
     * Print the completion for the given shell to stdout. Only <code>--completion bash</code> is supported at this time.
     */
    COMPLETION(null, null, null, OptionType.STRING, Flags.OPTIONAL, "mvnd:--completion"),
    /**
     * Delete log files under the <code>mvnd.registry</code> directory that are older than <code>mvnd.logPurgePeriod</code>
     */
    PURGE(null, null, null, OptionType.VOID, Flags.OPTIONAL, "mvnd:--purge"),
    /** Prints the status of daemon instances registered in the registry specified by <code>mvnd.registry</code> */
    STATUS(null, null, null, OptionType.VOID, Flags.OPTIONAL, "mvnd:--status"),
    /** Stop all daemon instances registered in the registry specified by <code>mvnd.registry</code> */
    STOP(null, null, null, OptionType.VOID, Flags.OPTIONAL, "mvnd:--stop"),
    /** Terminal diagnosis */
    DIAG(null, null, null, OptionType.VOID, Flags.OPTIONAL, "mvnd:--diag"),
    /** Use one thread, no log buffering and the default project builder to behave like a standard maven */
    SERIAL("mvnd.serial", null, Boolean.FALSE, OptionType.VOID, Flags.OPTIONAL, "mvnd:-1", "mvnd:--serial"),

    //
    // System properties
    //
    /** Java home for starting the daemon. */
    JAVA_HOME("java.home", "JAVA_HOME", null, OptionType.PATH, Flags.DOCUMENTED_AS_DISCRIMINATING),
    /**
     * The daemon installation directory. The client normally sets this according to where its <code>mvnd</code>
     * executable is located
     */
    MVND_HOME("mvnd.home", "MVND_HOME", null, OptionType.PATH, Flags.DISCRIMINATING),
    /** The user home directory */
    USER_HOME("user.home", null, null, OptionType.PATH, Flags.NONE),
    /** The current working directory */
    USER_DIR("user.dir", null, null, OptionType.PATH, Flags.NONE),
    /** The JDK_JAVA_OPTIONS option */
    JDK_JAVA_OPTIONS("jdk.java.options", "JDK_JAVA_OPTIONS", "", OptionType.STRING, Flags.DISCRIMINATING),

    //
    // Maven properties
    //
    /** The path to the Maven local repository */
    MAVEN_REPO_LOCAL("maven.repo.local", null, null, OptionType.PATH, Flags.DISCRIMINATING | Flags.OPTIONAL),
    /** The location of the maven settings file */
    MAVEN_SETTINGS(
            "maven.settings",
            null,
            null,
            OptionType.PATH,
            Flags.DISCRIMINATING | Flags.OPTIONAL,
            "mvn:-s",
            "mvn:--settings"),
    /** The pom or directory to build */
    MAVEN_FILE(null, null, null, OptionType.PATH, Flags.NONE, "mvn:-f", "mvn:--file"),
    /** The root directory of the current multi module Maven project */
    MAVEN_MULTIMODULE_PROJECT_DIRECTORY("maven.multiModuleProjectDirectory", null, null, OptionType.PATH, Flags.NONE),
    /** Log file */
    MAVEN_LOG_FILE(null, null, null, OptionType.PATH, Flags.INTERNAL, "mvn:-l", "mvn:--log-file"),
    /** Batch mode */
    MAVEN_BATCH_MODE(null, null, null, OptionType.BOOLEAN, Flags.INTERNAL, "mvn:-B", "mvn:--batch-mode"),
    /** Debug */
    MAVEN_DEBUG(null, null, null, OptionType.BOOLEAN, Flags.INTERNAL, "mvn:-X", "mvn:--debug"),
    /** Version */
    MAVEN_VERSION(null, null, null, OptionType.BOOLEAN, Flags.INTERNAL, "mvn:-v", "mvn:-version", "mvn:--version"),
    /** Show version */
    MAVEN_SHOW_VERSION(null, null, null, OptionType.BOOLEAN, Flags.INTERNAL, "mvn:-V", "mvn:--show-version"),
    /** Define */
    MAVEN_DEFINE(null, null, null, OptionType.STRING, Flags.INTERNAL, "mvn:-D", "mvn:--define"),
    /** Whether the output should be styled using ANSI color codes; possible values: auto, always, never */
    MAVEN_COLOR("style.color", null, "auto", OptionType.STRING, Flags.OPTIONAL, "mvnd:--color"),

    //
    // mvnd properties
    //

    /**
     * The location of the user supplied <code>mvnd.properties</code> file.
     */
    MVND_PROPERTIES_PATH("mvnd.propertiesPath", "MVND_PROPERTIES_PATH", null, OptionType.PATH, Flags.NONE),
    /**
     * The directory under which the daemon stores its registry, log files, etc.
     * Default: <code>${user.home}/.m2/mvnd</code>
     */
    MVND_DAEMON_STORAGE("mvnd.daemonStorage", "MVND_DAEMON_STORAGE", null, OptionType.PATH, Flags.NONE),
    /**
     * The path to the daemon registry.
     * Default: <code>${mvnd.daemonStorage}/registry.bin</code>
     */
    MVND_REGISTRY("mvnd.registry", null, null, OptionType.PATH, Flags.NONE),
    /**
     * If <code>true</code> the log messages are displayed continuously like with stock Maven; otherwise buffer the
     * messages and output at the end of the build, grouped by module. Passing <code>-B</code> or
     * <code>--batch-mode</code> on the command line enables this too for the given build.
     */
    MVND_NO_BUFERING("mvnd.noBuffering", null, Boolean.FALSE, OptionType.BOOLEAN, Flags.NONE),
    /**
     * The number of log lines to display for each Maven module that is built in parallel. The value can be increased
     * or decreased by pressing + or - key during the build respectively. This option has no effect with
     * <code>-Dmvnd.noBuffering=true</code>, <code>-B</code> or <code>--batch-mode</code>.
     */
    MVND_ROLLING_WINDOW_SIZE("mvnd.rollingWindowSize", null, "0", OptionType.INTEGER, Flags.NONE),
    /**
     * Daemon log files older than this value will be removed automatically.
     */
    MVND_LOG_PURGE_PERIOD("mvnd.logPurgePeriod", null, "7 days", OptionType.DURATION, Flags.NONE),
    /**
     * If <code>true</code>, the client and daemon will run in the same JVM that exits when the build is finished;
     * otherwise the client starts/connects to a long living daemon process. This option is only available with
     * non-native clients and is useful mostly for debugging.
     */
    MVND_NO_DAEMON("mvnd.noDaemon", "MVND_NO_DAEMON", Boolean.FALSE, OptionType.BOOLEAN, Flags.DISCRIMINATING),

    /**
     * If <code>true</code>, the daemon will not use its in-memory metadata cache and instead re-read the
     * metadata from the pom.xml files in the local repository. This is mostly useful for testing purposes.
     */
    MVND_NO_MODEL_CACHE("mvnd.noModelCache", null, Boolean.FALSE, OptionType.BOOLEAN, Flags.OPTIONAL),

    /**
     * If <code>true</code>, the daemon will be launched in debug mode with the following JVM argument:
     * <code>-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000</code>; otherwise the debug argument is
     * not passed to the daemon.
     */
    MVND_DEBUG("mvnd.debug", null, Boolean.FALSE, OptionType.BOOLEAN, Flags.DISCRIMINATING),
    /**
     * The tcp address used to launch the debug mode. Defaults to <code>8000</code>, which is similar to
     * <code>localhost:8000</code>.  In order to remote debug from a different computer, you need to allow
     * remote connections using <code>*:8000</code> for example.  Use a port with a value of <code>0</code>
     * to have <code>mvnd</code> to choose one.
     */
    MVND_DEBUG_ADDRESS("mvnd.debug.address", null, "8000", OptionType.STRING, Flags.DISCRIMINATING),
    /**
     * A time period after which an unused daemon will terminate by itself.
     */
    MVND_IDLE_TIMEOUT("mvnd.idleTimeout", null, "3 hours", OptionType.DURATION, Flags.DISCRIMINATING),
    /**
     * If the daemon does not send any message to the client in this period of time, send a keep-alive message so that
     * the client knows that the daemon is still alive.
     */
    MVND_KEEP_ALIVE("mvnd.keepAlive", null, "100 ms", OptionType.DURATION, Flags.DISCRIMINATING),
    /**
     * The maximum number of keep alive messages that can be missed by the client before the client considers the daemon
     * to be dead.
     */
    MVND_MAX_LOST_KEEP_ALIVE("mvnd.maxLostKeepAlive", null, 30, OptionType.INTEGER, Flags.NONE),
    /**
     * The minimum number of threads to use when constructing the default <code>-T</code> parameter for the daemon.
     * This value is ignored if the user passes <code>-T</code>, <code>--threads</code> or <code>-Dmvnd.threads</code>
     * on the command line or if he sets <code>mvnd.threads</code> in <code>~/.m2/mvnd.properties</code>.
     */
    MVND_MIN_THREADS("mvnd.minThreads", null, 1, OptionType.INTEGER, Flags.NONE),
    /**
     * The number of threads to pass to the daemon; same syntax as Maven's <code>-T</code>/<code>--threads</code>
     * option.
     */
    MVND_THREADS("mvnd.threads", null, null, OptionType.STRING, Flags.NONE, "mvn:-T", "mvn:--threads"),
    /**
     * The builder implementation the daemon should use.
     */
    MVND_BUILDER("mvnd.builder", null, "smart", OptionType.STRING, Flags.NONE, "mvn:-b", "mvn:--builder"),
    /**
     * An ID for a newly started daemon.
     */
    MVND_ID("mvnd.id", null, null, OptionType.STRING, Flags.INTERNAL),
    /**
     * Internal option to specify the maven extension classpath.
     */
    MVND_EXT_CLASSPATH("mvnd.extClasspath", null, null, OptionType.STRING, Flags.DISCRIMINATING | Flags.INTERNAL),
    /**
     * Internal option to specify the list of maven extension to register.
     */
    MVND_CORE_EXTENSIONS("mvnd.coreExtensions", null, null, OptionType.STRING, Flags.DISCRIMINATING | Flags.INTERNAL),
    /**
     * Internal option to specify comma separated list of maven extension G:As to exclude (to not load them from
     * .mvn/extensions.xml). This option makes possible for example that a project that with vanilla Maven would
     * use takari-smart-builder extension, remain buildable with mvnd (where use of this extension would cause issues).
     * Value is expected as comma separated {@code g1:a1,g2:a2} pairs.
     */
    MVND_CORE_EXTENSIONS_EXCLUDE(
            "mvnd.coreExtensionsExclude",
            null,
            "io.takari.maven:takari-smart-builder",
            OptionType.STRING,
            Flags.OPTIONAL),
    /**
     * The <code>-Xms</code> value to pass to the daemon.
     * This option takes precedence over options specified in <code>-Dmvnd.jvmArgs</code>.
     */
    MVND_MIN_HEAP_SIZE("mvnd.minHeapSize", null, null, OptionType.MEMORY_SIZE, Flags.DISCRIMINATING | Flags.OPTIONAL),
    /**
     * The <code>-Xmx</code> value to pass to the daemon.
     * This option takes precedence over options specified in <code>-Dmvnd.jvmArgs</code>.
     */
    MVND_MAX_HEAP_SIZE("mvnd.maxHeapSize", null, null, OptionType.MEMORY_SIZE, Flags.DISCRIMINATING | Flags.OPTIONAL),
    /**
     * The <code>-Xss</code> value to pass to the daemon.
     * This option takes precedence over options specified in <code>-Dmvnd.jvmArgs</code>.
     */
    MVND_THREAD_STACK_SIZE(
            "mvnd.threadStackSize", null, null, OptionType.MEMORY_SIZE, Flags.DISCRIMINATING | Flags.OPTIONAL),
    /**
     * Additional JVM args to pass to the daemon.
     * The content of the <code>.mvn/jvm.config</code> file will prepended (and thus with
     * a lesser priority) to the user supplied value for this parameter before being used
     * as startup options for the daemon JVM.
     */
    MVND_JVM_ARGS("mvnd.jvmArgs", null, null, OptionType.STRING, Flags.DISCRIMINATING | Flags.OPTIONAL),
    /**
     * If <code>true</code>, the <code>-ea</code> option will be passed to the daemon; otherwise the <code>-ea</code>
     * option is not passed to the daemon.
     */
    MVND_ENABLE_ASSERTIONS("mvnd.enableAssertions", null, Boolean.FALSE, OptionType.BOOLEAN, Flags.DISCRIMINATING),
    /**
     * The daemon will check this often whether it should exit.
     */
    MVND_EXPIRATION_CHECK_DELAY(
            "mvnd.expirationCheckDelay", null, "10 seconds", OptionType.DURATION, Flags.DISCRIMINATING),
    /**
     * Period after which idle duplicate daemons will be shut down. Duplicate daemons are daemons with the same set of
     * discriminating start parameters.
     */
    MVND_DUPLICATE_DAEMON_GRACE_PERIOD(
            "mvnd.duplicateDaemonGracePeriod", null, "10 seconds", OptionType.DURATION, Flags.DISCRIMINATING),
    /**
     * Internal property to tell the daemon the width of the terminal
     */
    MVND_TERMINAL_WIDTH("mvnd.terminalWidth", null, 0, OptionType.INTEGER, Flags.INTERNAL),
    /**
     * Internal property to tell the daemon which JAVA_HOME was used to start it. It needs to be passed explicitly
     * because the value may differ from what the daemon sees through <code>System.getProperty("java.home")</code>.
     */
    MVND_JAVA_HOME("mvnd.java.home", null, null, OptionType.PATH, Flags.INTERNAL),
    /**
     * Log mojos execution time at the end of the build.
     */
    MVND_BUILD_TIME("mvnd.buildTime", null, null, OptionType.BOOLEAN, Flags.NONE),
    /**
     * Socket family to use
     */
    MVND_SOCKET_FAMILY("mvnd.socketFamily", null, "inet", OptionType.STRING, Flags.DISCRIMINATING),
    /**
     * Pattern that will force eviction of the plugin realms if one of its dependencies matches.
     * The overall pattern is a comma separated list of either:
     * <ul>
     * <li>a glob pattern starting with <code>'glob:'</code> (the default syntax if no scheme is specified),</li>
     * <li>a regex pattern starting with <code>'regex:'</code>,</li>
     * <li>a maven expression, either <code>'mvn:[groupId]:[artifactId]:[version]'</code>,
     * <code>'mvn:[groupId]:[artifactId]'</code> or <code>'mvn:[artifactId]</code>'.</li>
     * </ul>
     * This pattern will be evaluated against the full path of the dependencies, so it is usually desirable to
     * start with <code>'glob:**&#47;'</code> to support any location of the local repository.
     */
    MVND_PLUGIN_REALM_EVICT_PATTERN("mvnd.pluginRealmEvictPattern", null, "", OptionType.STRING, Flags.OPTIONAL),
    /**
     * Overall timeout to connect to a daemon.
     */
    MVND_CONNECT_TIMEOUT("mvnd.connectTimeout", null, "10 seconds", OptionType.DURATION, Flags.NONE),
    /**
     * Timeout to establish the socket connection.
     */
    MVND_SOCKET_CONNECT_TIMEOUT("mvnd.socketConnectTimeout", null, "1 seconds", OptionType.DURATION, Flags.NONE),
    /**
     * Timeout to connect to a cancelled daemon.
     */
    MVND_CANCEL_CONNECT_TIMEOUT("mvnd.cancelConnectTimeout", null, "3 seconds", OptionType.DURATION, Flags.NONE),
    ;

    static Properties properties;

    public static void setProperties(Properties properties) {
        Environment.properties = properties;
    }

    public static String getProperty(String property) {
        Properties props = Environment.properties;
        if (props == null) {
            props = System.getProperties();
        }
        return props.getProperty(property);
    }

    private final String property;
    private final String environmentVariable;
    private final String default_;
    private final int flags;
    private final OptionType type;
    private final Map<String, OptionOrigin> options;

    Environment(
            String property,
            String environmentVariable,
            Object default_,
            OptionType type,
            int flags,
            String... options) {
        if (property == null && options.length == 0) {
            throw new IllegalArgumentException(
                    "An " + Environment.class.getSimpleName() + " entry must have property or options set");
        }
        this.property = property;
        this.environmentVariable = environmentVariable;
        this.default_ = default_ != null ? default_.toString() : null;
        if ((flags & Flags.DISCRIMINATING) != 0) {
            this.flags = (flags | Flags.DOCUMENTED_AS_DISCRIMINATING);
        } else {
            this.flags = flags;
        }
        this.type = type;
        if (options.length == 0) {
            this.options = Collections.emptyMap();
        } else {
            final Map<String, OptionOrigin> optMap = new LinkedHashMap<>();
            for (String opt : options) {
                OPTION_ORIGIN_SEARCH:
                {
                    for (OptionOrigin oo : OptionOrigin.values()) {
                        if (opt.startsWith(oo.prefix)) {
                            optMap.put(opt.substring(oo.prefix.length()), oo);
                            break OPTION_ORIGIN_SEARCH;
                        }
                    }
                    throw new IllegalArgumentException(
                            "Unexpected option prefix: '" + opt + "'; Options should start with any of "
                                    + Stream.of(OptionOrigin.values())
                                            .map(oo -> oo.prefix)
                                            .collect(Collectors.joining(",")));
                }
            }
            this.options = Collections.unmodifiableMap(optMap);
        }
    }

    public String getProperty() {
        return property;
    }

    public String getEnvironmentVariable() {
        return environmentVariable;
    }

    public String getDefault() {
        return default_;
    }

    public Set<String> getOptions() {
        return options.keySet();
    }

    public Map<String, OptionOrigin> getOptionMap() {
        return options;
    }

    public OptionType getType() {
        return type;
    }

    public boolean isDiscriminating() {
        return (flags & Flags.DISCRIMINATING) != 0;
    }

    public boolean isDocumentedAsDiscriminating() {
        return (flags & Flags.DOCUMENTED_AS_DISCRIMINATING) != 0;
    }

    public boolean isInternal() {
        return (flags & Flags.INTERNAL) != 0;
    }

    public boolean isOptional() {
        return (flags & Flags.OPTIONAL) != 0;
    }

    public String asString() {
        String val = getProperty(property);
        if (val == null) {
            throw new IllegalStateException("The system property " + property + " is missing");
        }
        return val;
    }

    public Optional<String> asOptional() {
        String val = getProperty(property);
        if (val != null) {
            return Optional.of(val);
        } else if (isOptional()) {
            return Optional.empty();
        } else {
            throw new IllegalStateException("The system property " + property + " is missing");
        }
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

    public String asDaemonOpt(String value) {
        return property + "=" + type.normalize(value);
    }

    public void addSystemProperty(Collection<String> args, String value) {
        args.add("-D" + property + "=" + type.normalize(value));
    }

    public void addCommandLineOption(Collection<String> args, String value) {
        if (!options.isEmpty()) {
            args.add(options.keySet().iterator().next());
            args.add(type.normalize(value));
        } else {
            args.add("-D" + property + "=" + type.normalize(value));
        }
    }

    public boolean hasCommandLineOption(Collection<String> args) {
        final String[] prefixes = getPrefixes();
        return args.stream().anyMatch(arg -> Stream.of(prefixes).anyMatch(arg::startsWith));
    }

    public String getCommandLineOption(Collection<String> args) {
        return getCommandLineOption(args, false);
    }

    public String removeCommandLineOption(Collection<String> args) {
        return getCommandLineOption(args, true);
    }

    String getCommandLineOption(Collection<String> args, boolean remove) {
        final String[] prefixes = getPrefixes();
        String value = null;
        for (Iterator<String> it = args.iterator(); it.hasNext(); ) {
            String arg = it.next();
            if (Stream.of(prefixes).anyMatch(arg::startsWith)) {
                if (remove) {
                    it.remove();
                }
                if (type == OptionType.VOID) {
                    value = "";
                } else {
                    String opt = Stream.of(prefixes)
                            .filter(arg::startsWith)
                            .max(Comparator.comparing(String::length))
                            .get();
                    value = arg.substring(opt.length());
                    if (value.isEmpty()) {
                        if (it.hasNext()) {
                            value = it.next();
                            if (remove) {
                                it.remove();
                            }
                        }
                    } else {
                        if (value.charAt(0) == '=') {
                            value = value.substring(1);
                        }
                    }
                }
            }
        }
        return value;
    }

    private String[] getPrefixes() {
        final String[] prefixes;
        if (options.isEmpty()) {
            prefixes = new String[] {"-D" + property + "="};
        } else if (property != null) {
            prefixes = new String[options.size() + 1];
            options.keySet().toArray(prefixes);
            prefixes[options.size()] = "-D" + property + "=";
        } else {
            prefixes = options.keySet().toArray(new String[0]);
        }
        return prefixes;
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

    public static Stream<DocumentedEnumEntry<Environment>> documentedEntries() {
        Properties props = new Properties();
        Environment[] values = values();
        final String cliOptionsPath = values[0].getClass().getSimpleName() + ".javadoc.properties";
        try (InputStream in = Environment.class.getResourceAsStream(cliOptionsPath)) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + cliOptionsPath, e);
        }
        return Stream.of(values)
                .filter(env -> !env.isInternal())
                .sorted(Comparator.<Environment, String>comparing(env -> env.property != null ? env.property : "")
                        .thenComparing(env -> !env.options.isEmpty()
                                ? env.options.keySet().iterator().next()
                                : ""))
                .map(env -> new DocumentedEnumEntry<>(env, props.getProperty(env.name())));
    }

    public enum OptionOrigin {
        mvn,
        mvnd;

        private final String prefix;

        private OptionOrigin() {
            this.prefix = name() + ":";
        }
    }

    /**
     * The values of {@link Environment#MAVEN_COLOR} option.
     */
    public enum Color {
        always,
        never,
        auto;

        public static Optional<Color> of(String color) {
            return color == null ? Optional.empty() : Optional.of(Color.valueOf(color));
        }
    }

    public static class DocumentedEnumEntry<E> {

        private final E entry;
        private final String javaDoc;

        public DocumentedEnumEntry(E entry, String javaDoc) {
            this.entry = entry;
            this.javaDoc = javaDoc;
        }

        public E getEntry() {
            return entry;
        }

        public String getJavaDoc() {
            return javaDoc;
        }
    }

    static class Flags {
        private static final int NONE = 0b0;
        /**
         * Implies {@link #DOCUMENTED_AS_DISCRIMINATING} - this is implemented in
         * {@link Environment#Environment(String, String, Object, OptionType, int, String...)}
         */
        private static final int DISCRIMINATING = 0b1;

        private static final int INTERNAL = 0b10;
        private static final int OPTIONAL = 0b100;
        /** Set automatically for entries having {@link #DISCRIMINATING} */
        private static final int DOCUMENTED_AS_DISCRIMINATING = 0b1000;
    }
}
