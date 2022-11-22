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
package org.mvndaemon.mvnd.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.client.DaemonParameters.EnvValue;
import org.mvndaemon.mvnd.client.DaemonParameters.ValueSource;
import org.mvndaemon.mvnd.common.Environment;

public class EnvironmentTest {

    @Test
    void arguments() {
        assertEquals("foo=bar", Environment.MAVEN_DEFINE.removeCommandLineOption(list("-Dfoo=bar")));
        assertEquals("foo=bar", Environment.MAVEN_DEFINE.removeCommandLineOption(list("-D", "foo=bar")));
        assertEquals("foo=bar", Environment.MAVEN_DEFINE.removeCommandLineOption(list("--define", "foo=bar")));
        assertEquals("foo=bar", Environment.MAVEN_DEFINE.removeCommandLineOption(list("--define=foo=bar")));

        assertEquals("foo", Environment.MAVEN_DEFINE.removeCommandLineOption(list("-D=foo")));
        assertEquals("foo", Environment.MAVEN_DEFINE.removeCommandLineOption(list("-Dfoo")));
        assertEquals("foo", Environment.MAVEN_DEFINE.removeCommandLineOption(list("-D", "foo")));

        assertEquals("foo=", Environment.MAVEN_DEFINE.removeCommandLineOption(list("-Dfoo=")));

        assertEquals("", Environment.MAVEN_DEFINE.removeCommandLineOption(list("-D")));
        assertEquals("", Environment.MAVEN_DEFINE.removeCommandLineOption(list("--define")));
    }

    private List<String> list(String... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    @Test
    void prop() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            env.props("mvnd.home", "/maven/home/prop");
            assertEquals(
                    "/maven/home/prop",
                    DaemonParameters.systemProperty(Environment.MVND_HOME).asString());
        }
    }

    @Test
    void env() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            env.env("MVND_HOME", "/maven/home/env");
            assertEquals(
                    "/maven/home/env",
                    DaemonParameters.environmentVariable(Environment.MVND_HOME).asString());
        }
    }

    @Test
    void localProps() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            final Properties localProps = new Properties();
            localProps.put("mvnd.home", "/maven/home/local");
            assertEquals(
                    Paths.get("/maven/home/local"),
                    DaemonParameters.environmentVariable(Environment.MVND_HOME)
                            .orSystemProperty()
                            .orLocalProperty(path -> localProps, Paths.get("/local/properties"))
                            .orFail()
                            .asPath());
        }
    }

    @Test
    void envBeforeProp() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            env.props("mvnd.home", "/maven/home/prop");
            env.env("MVND_HOME", "/maven/home/env");
            assertEquals(
                    "/maven/home/env",
                    DaemonParameters.environmentVariable(Environment.MVND_HOME)
                            .orSystemProperty()
                            .asString());
        }
    }

    @Test
    void fail() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            try {
                assertEquals(
                        "/maven/home/env",
                        DaemonParameters.environmentVariable(Environment.MVND_HOME)
                                .orSystemProperty()
                                .orFail()
                                .asString());
                Assertions.fail("IllegalStateException expected");
            } catch (IllegalStateException e) {
                assertEquals(
                        "Could not get value for Environment.MVND_HOME from any of the following sources: environment variable MVND_HOME, system property mvnd.home",
                        e.getMessage());
            }
        }
    }

    @Test
    void cygwin() {
        assertEquals("C:\\jdk-11.0.2\\", Environment.cygpath("/cygdrive/c/jdk-11.0.2/"));
    }

    @Test
    void emptyBooleanEnvValueIsTrue() {
        final String EMPTY_STRING = "";
        final EnvValue envVal = new EnvValue(
                Environment.MVND_NO_BUFERING,
                new ValueSource(sb -> sb.append("envValueAsBoolean"), () -> EMPTY_STRING));
        assertEquals(true, envVal.asBoolean());
    }

    static class EnvironmentResource implements AutoCloseable {

        private final Properties props = new Properties();
        private final Map<String, String> env = new HashMap<>();

        public EnvironmentResource() {
            DaemonParameters.EnvValue.env = env;
            Environment.setProperties(props);
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
            DaemonParameters.EnvValue.env = System.getenv();
            Environment.setProperties(System.getProperties());
        }
    }
}
