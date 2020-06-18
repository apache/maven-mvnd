package org.jboss.fuse.mvnd.client;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnvironmentTest {

    @Test
    void prop() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            env.props("maven.home", "/maven/home/prop");
            Assertions.assertEquals("/maven/home/prop", Environment.MAVEN_HOME.systemProperty().asString());
        }
    }

    @Test
    void env() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            env.env("MAVEN_HOME", "/maven/home/env");
            Assertions.assertEquals("/maven/home/env", Environment.MAVEN_HOME.environmentVariable().asString());
        }
    }

    @Test
    void localProps() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            final Properties localProps = new Properties();
            localProps.put("maven.home", "/maven/home/local");
            Assertions.assertEquals(Paths.get("/maven/home/local"),
                    Environment.MAVEN_HOME
                            .environmentVariable()
                            .orSystemProperty()
                            .orLocalProperty(() -> localProps, Paths.get("/local/properties"))
                            .orFail()
                            .asPath());
        }
    }

    @Test
    void envBeforeProp() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            env.props("maven.home", "/maven/home/prop");
            env.env("MAVEN_HOME", "/maven/home/env");
            Assertions.assertEquals("/maven/home/env",
                    Environment.MAVEN_HOME
                            .environmentVariable()
                            .orSystemProperty()
                            .asString());
        }
    }

    @Test
    void fail() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            try {
                Assertions.assertEquals("/maven/home/env",
                        Environment.MAVEN_HOME
                                .environmentVariable()
                                .orSystemProperty()
                                .orFail()
                                .asString());
                Assertions.fail("IllegalStateException expected");
            } catch (IllegalStateException e) {
                Assertions.assertEquals(
                        "Could not get value for Environment.MAVEN_HOME from any of the following sources: environment variable MAVEN_HOME, system property maven.home",
                        e.getMessage());
            }
        }
    }

    static class EnvironmentResource implements AutoCloseable {

        private final Properties props = new Properties();
        private final Map<String, String> env = new HashMap<>();

        public EnvironmentResource() {
            Environment.env = env;
            Environment.properties = props;
        }

        public void props(String... props) {
            int i = 0;
            while (i < props.length) {
                this.props.setProperty(props[i++], props[i++]);
            }
        }

        public void env(String... env) {
            int i = 0;
            while (i < env.length) {
                this.env.put(env[i++], env[i++]);
            }
        }

        @Override
        public void close() {
            Environment.env = System.getenv();
            Environment.properties = System.getProperties();
        }

    }

}
