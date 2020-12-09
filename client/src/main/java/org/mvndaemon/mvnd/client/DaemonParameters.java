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
package org.mvndaemon.mvnd.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.cli.internal.extension.model.CoreExtension;
import org.apache.maven.cli.internal.extension.model.io.xpp3.CoreExtensionsXpp3Reader;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.mvndaemon.mvnd.common.BuildProperties;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.Os;
import org.mvndaemon.mvnd.common.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hold all daemon configuration
 */
public class DaemonParameters {

    public static final String LOG_EXTENSION = ".log";

    private static final Logger LOG = LoggerFactory.getLogger(DaemonParameters.class);
    private static final String EXT_CLASS_PATH = "maven.ext.class.path";
    private static final String EXTENSIONS_FILENAME = ".mvn/extensions.xml";

    protected final Map<Path, Properties> mvndProperties = new ConcurrentHashMap<>();
    protected final Function<Path, Properties> provider = path -> mvndProperties.computeIfAbsent(path,
            p -> loadProperties(path));
    private final Map<String, String> properties;

    public DaemonParameters() {
        this.properties = Collections.emptyMap();
    }

    protected DaemonParameters(PropertiesBuilder propertiesBuilder) {
        this.properties = propertiesBuilder.build();
    }

    public List<String> getDaemonOpts() {
        return discriminatingValues()
                .map(envValue -> envValue.envKey.asDaemonOpt(envValue.asString()))
                .collect(Collectors.toList());
    }

    public Map<String, String> getDaemonOptsMap() {
        return discriminatingValues()
                .collect(Collectors.toMap(
                        envValue -> envValue.envKey.getProperty(),
                        EnvValue::asString));
    }

    Stream<EnvValue> discriminatingValues() {
        return Arrays.stream(Environment.values())
                .filter(Environment::isDiscriminating)
                .map(env -> property(env))
                .filter(EnvValue::isSet);
    }

    public void discriminatingCommandLineOptions(List<String> args) {
        discriminatingValues()
                .forEach(envValue -> envValue.envKey.appendAsCommandLineOption(args, envValue.asString()));
    }

    public Path mvndHome() {
        return value(Environment.MVND_HOME)
                .or(new ValueSource(
                        description -> description.append("path relative to the mvnd executable"),
                        this::mvndHomeFromExecutable))
                .orSystemProperty()
                .orEnvironmentVariable()
                .orLocalProperty(provider, suppliedPropertiesPath())
                .orLocalProperty(provider, localPropertiesPath())
                .orLocalProperty(provider, userPropertiesPath())
                .orFail()
                .asPath()
                .toAbsolutePath().normalize();
    }

    private String mvndHomeFromExecutable() {
        Optional<String> cmd = ProcessHandle.current().info().command();
        if (Environment.isNative() && cmd.isPresent()) {
            final Path mvndH = Paths.get(cmd.get()).getParent().getParent();
            if (mvndH != null) {
                final Path mvndDaemonLib = mvndH
                        .resolve("mvn/lib/ext/mvnd-daemon-" + BuildProperties.getInstance().getVersion() + ".jar");
                if (Files.exists(mvndDaemonLib)) {
                    return mvndH.toString();
                }
            }
        }
        return null;
    }

    public Path javaHome() {
        final Path result = value(Environment.JAVA_HOME)
                .orEnvironmentVariable()
                .orLocalProperty(provider, suppliedPropertiesPath())
                .orLocalProperty(provider, localPropertiesPath())
                .orLocalProperty(provider, userPropertiesPath())
                .orLocalProperty(provider, globalPropertiesPath())
                .orSystemProperty()
                .orFail()
                .asPath();
        try {
            return result.toRealPath();
        } catch (IOException e) {
            throw new RuntimeException("Could not get a real path from path " + result);
        }
    }

    public Path userDir() {
        return value(Environment.USER_DIR)
                .orSystemProperty()
                .orFail()
                .asPath()
                .toAbsolutePath();
    }

    public Path userHome() {
        return value(Environment.USER_HOME)
                .orSystemProperty()
                .orFail()
                .asPath()
                .toAbsolutePath();
    }

    public Path suppliedPropertiesPath() {
        return value(Environment.MVND_PROPERTIES_PATH)
                .orEnvironmentVariable()
                .orSystemProperty()
                .asPath();
    }

    public Path localPropertiesPath() {
        return multiModuleProjectDirectory().resolve(".mvn/mvnd.properties");
    }

    public Path userPropertiesPath() {
        return userHome().resolve(".m2/mvnd.properties");
    }

    public Path globalPropertiesPath() {
        return mvndHome().resolve("conf/mvnd.properties");
    }

    public Path daemonStorage() {
        return value(Environment.MVND_DAEMON_STORAGE)
                .orSystemProperty()
                .orDefault(
                        () -> userHome().resolve(".m2/mvnd/registry/" + BuildProperties.getInstance().getVersion()).toString())
                .asPath();
    }

    public Path registry() {
        return daemonStorage().resolve("registry.bin");
    }

    public Path daemonLog(String daemon) {
        return daemonStorage().resolve("daemon-" + daemon + LOG_EXTENSION);
    }

    public Path daemonOutLog(String daemon) {
        return daemonStorage().resolve("daemon-" + daemon + ".out" + LOG_EXTENSION);
    }

    public Path multiModuleProjectDirectory() {
        return value(Environment.MAVEN_MULTIMODULE_PROJECT_DIRECTORY)
                .orSystemProperty()
                .orDefault(() -> findDefaultMultimoduleProjectDirectory(userDir()))
                .asPath()
                .toAbsolutePath().normalize();
    }

    public Path logbackConfigurationPath() {
        return property(Environment.LOGBACK_CONFIGURATION_FILE)
                .orDefault(() -> mvndHome().resolve("conf/logback.xml").toString())
                .orFail()
                .asPath();
    }

    public String minHeapSize() {
        return property(Environment.MVND_MIN_HEAP_SIZE).asString();
    }

    public String maxHeapSize() {
        return property(Environment.MVND_MAX_HEAP_SIZE).asString();
    }

    public String jvmArgs() {
        return property(Environment.MVND_JVM_ARGS).asString();
    }

    /**
     * @return the number of threads (same syntax as Maven's {@code -T}/{@code --threads} option) to pass to the daemon
     *         unless the user passes his own `-T` or `--threads`.
     */
    public String threads() {
        return property(Environment.MVND_THREADS)
                .orDefault(() -> String.valueOf(property(Environment.MVND_MIN_THREADS)
                        .asInt(m -> Math.max(Runtime.getRuntime().availableProcessors() - 1, m))))
                .orFail()
                .asString();
    }

    public String builder() {
        return property(Environment.MVND_BUILDER).orFail().asString();
    }

    /**
     * @return absolute normalized path to {@code settings.xml} or {@code null}
     */
    public Path settings() {
        return property(Environment.MAVEN_SETTINGS).asPath();
    }

    /**
     * @return absolute normalized path to local Maven repository or {@code null} if the server is supposed to use the
     *         default
     */
    public Path mavenRepoLocal() {
        return property(Environment.MAVEN_REPO_LOCAL).asPath();
    }

    /**
     * @return <code>true</code> if maven should be executed within this process instead of spawning a daemon.
     */
    public boolean noDaemon() {
        return value(Environment.MVND_NO_DAEMON)
                .orSystemProperty()
                .orEnvironmentVariable()
                .orDefault()
                .asBoolean();
    }

    /**
     * @param  newUserDir where to change the current directory to
     * @return            a new {@link DaemonParameters} with {@code userDir} set to the given {@code newUserDir}
     */
    public DaemonParameters cd(Path newUserDir) {
        return new DaemonParameters(new PropertiesBuilder()
                .putAll(this.properties)
                .put(Environment.USER_DIR, newUserDir));
    }

    public Duration keepAlive() {
        return property(Environment.MVND_KEEP_ALIVE).orFail().asDuration();
    }

    public int maxLostKeepAlive() {
        return property(Environment.MVND_MAX_LOST_KEEP_ALIVE).orFail().asInt();
    }

    public boolean noBuffering() {
        return property(Environment.MVND_NO_BUFERING).orFail().asBoolean();
    }

    public int rollingWindowSize() {
        return property(Environment.MVND_ROLLING_WINDOW_SIZE).orFail().asInt();
    }

    public Duration purgeLogPeriod() {
        return property(Environment.MVND_LOG_PURGE_PERIOD).orFail().asDuration();
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

    public EnvValue property(Environment env) {
        return value(env)
                .orSystemProperty()
                .orLocalProperty(provider, suppliedPropertiesPath())
                .orLocalProperty(provider, localPropertiesPath())
                .orLocalProperty(provider, userPropertiesPath())
                .orLocalProperty(provider, globalPropertiesPath())
                .orDefault(() -> defaultValue(env));
    }

    protected EnvValue value(Environment env) {
        return new EnvValue(env, new ValueSource(
                description -> description.append("value: ").append(env.getProperty()),
                () -> properties.get(env.getProperty())));
    }

    public static EnvValue systemProperty(Environment env) {
        return new EnvValue(env, EnvValue.systemPropertySource(env));
    }

    public static EnvValue environmentVariable(Environment env) {
        return new EnvValue(env, EnvValue.environmentVariableSource(env));
    }

    public static EnvValue fromValueSource(Environment env, ValueSource valueSource) {
        return new EnvValue(env, valueSource);
    }

    private String defaultValue(Environment env) {
        if (env == Environment.MVND_EXT_CLASSPATH) {
            List<String> cp = parseExtClasspath(userHome());
            return String.join(",", cp);
        } else if (env == Environment.MVND_CORE_EXTENSIONS) {
            try {
                List<String> extensions = readCoreExtensionsDescriptor(multiModuleProjectDirectory()).stream()
                        .map(e -> e.getGroupId() + ":" + e.getArtifactId() + ":" + e.getVersion())
                        .collect(Collectors.toList());
                return String.join(",", extensions);
            } catch (IOException | XmlPullParserException e) {
                throw new RuntimeException("Unable to parse core extensions", e);
            }
        } else {
            return env.getDefault();
        }
    }

    private static List<String> parseExtClasspath(Path userDir) {
        String extClassPath = System.getProperty(EXT_CLASS_PATH);
        List<String> jars = new ArrayList<>();
        if (StringUtils.isNotEmpty(extClassPath)) {
            for (String jar : StringUtils.split(extClassPath, File.pathSeparator)) {
                Path path = userDir.resolve(jar).toAbsolutePath();
                jars.add(path.toString());
            }
        }
        return jars;
    }

    private static List<CoreExtension> readCoreExtensionsDescriptor(Path multiModuleProjectDirectory)
            throws IOException, XmlPullParserException {
        if (multiModuleProjectDirectory == null) {
            return Collections.emptyList();
        }
        Path extensionsFile = multiModuleProjectDirectory.resolve(EXTENSIONS_FILENAME);
        if (!Files.exists(extensionsFile)) {
            return Collections.emptyList();
        }
        CoreExtensionsXpp3Reader parser = new CoreExtensionsXpp3Reader();
        try (InputStream is = Files.newInputStream(extensionsFile)) {
            return parser.read(is).getExtensions();
        }
    }

    private static Properties loadProperties(Path path) {
        Properties result = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                result.load(in);
            } catch (IOException e) {
                throw new RuntimeException("Could not read " + path);
            }
        }
        return result;
    }

    public static class PropertiesBuilder {
        private Map<String, String> properties = new LinkedHashMap<>();

        public PropertiesBuilder put(Environment envKey, Object value) {
            if (value == null) {
                properties.remove(envKey.getProperty());
            } else {
                properties.put(envKey.getProperty(), value.toString());
            }
            return this;
        }

        public PropertiesBuilder putAll(Map<String, String> props) {
            properties.putAll(props);
            return this;
        }

        public Map<String, String> build() {
            Map<String, String> props = properties;
            properties = null;
            return Collections.unmodifiableMap(props);
        }
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

        static Map<String, String> env = System.getenv();

        private final Environment envKey;
        private final ValueSource valueSource;
        protected EnvValue previous;

        public EnvValue(Environment envKey, ValueSource valueSource) {
            this.previous = null;
            this.envKey = envKey;
            this.valueSource = valueSource;
        }

        public EnvValue(EnvValue previous, Environment envKey, ValueSource valueSource) {
            this.previous = previous;
            this.envKey = envKey;
            this.valueSource = valueSource;
        }

        private static ValueSource systemPropertySource(Environment env) {
            String property = env.getProperty();
            if (property == null) {
                throw new IllegalStateException("Cannot use " + Environment.class.getName() + " for getting a system property");
            }
            return new ValueSource(
                    description -> description.append("system property ").append(property),
                    () -> Environment.getProperty(property));
        }

        private static ValueSource environmentVariableSource(Environment env) {
            String envVar = env.getEnvironmentVariable();
            if (envVar == null) {
                throw new IllegalStateException(
                        "Cannot use " + Environment.class.getName() + "." + env.name()
                                + " for getting an environment variable");
            }
            return new ValueSource(
                    description -> description.append("environment variable ").append(envVar),
                    () -> EnvValue.env.get(envVar));
        }

        public EnvValue orSystemProperty() {
            return new EnvValue(this, envKey, systemPropertySource(envKey));
        }

        public EnvValue orLocalProperty(Function<Path, Properties> provider, Path localPropertiesPath) {
            if (localPropertiesPath != null) {
                return new EnvValue(this, envKey, new ValueSource(
                        description -> description.append("property ").append(envKey.getProperty()).append(" in ")
                                .append(localPropertiesPath),
                        () -> provider.apply(localPropertiesPath).getProperty(envKey.getProperty())));
            } else {
                return this;
            }
        }

        public EnvValue orEnvironmentVariable() {
            return new EnvValue(this, envKey, environmentVariableSource(envKey));
        }

        public EnvValue or(ValueSource source) {
            return new EnvValue(this, envKey, source);
        }

        public EnvValue orDefault() {
            return orDefault(envKey::getDefault);
        }

        public EnvValue orDefault(Supplier<String> defaultSupplier) {
            return new EnvValue(this, envKey,
                    new ValueSource(sb -> sb.append("default: ").append(defaultSupplier.get()), defaultSupplier));
        }

        public EnvValue orFail() {
            return new EnvValue(this, envKey, new ValueSource(sb -> sb, () -> {
                throw couldNotgetValue();
            }));
        }

        IllegalStateException couldNotgetValue() {
            EnvValue val = this;
            final StringBuilder sb = new StringBuilder("Could not get value for ")
                    .append(Environment.class.getSimpleName())
                    .append(".").append(envKey.name()).append(" from any of the following sources: ");

            /*
             * Compose the description functions to invert the order thus getting the resolution order in the
             * message
             */
            Function<StringBuilder, StringBuilder> description = (s -> s);
            while (val != null) {
                description = description.compose(val.valueSource.descriptionFunction);
                val = val.previous;
                if (val != null) {
                    description = description.compose(s -> s.append(", "));
                }
            }
            description.apply(sb);
            return new IllegalStateException(sb.toString());
        }

        String get() {
            if (previous != null) {
                final String result = previous.get();
                if (result != null) {
                    return result;
                }
            }
            final String result = valueSource.valueSupplier.get();
            if (result != null && LOG.isTraceEnabled()) {
                StringBuilder sb = new StringBuilder("Loaded environment value for key [")
                        .append(envKey.name())
                        .append("] from ");
                valueSource.descriptionFunction.apply(sb);
                sb.append(": [")
                        .append(result)
                        .append(']');
                LOG.trace(sb.toString());
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
            String result = get();
            if (result != null && Os.current().isCygwin()) {
                result = Environment.cygpath(result);
            }
            return result == null ? null : Paths.get(result);
        }

        public boolean asBoolean() {
            return Boolean.parseBoolean(get());
        }

        public int asInt() {
            return Integer.parseInt(get());
        }

        public int asInt(IntUnaryOperator function) {
            return function.applyAsInt(asInt());
        }

        public Duration asDuration() {
            return TimeUtils.toDuration(get());
        }

        public boolean isSet() {
            if (get() != null) {
                return true;
            } else if (envKey.isOptional()) {
                return false;
            } else {
                throw couldNotgetValue();
            }
        }

    }
}
