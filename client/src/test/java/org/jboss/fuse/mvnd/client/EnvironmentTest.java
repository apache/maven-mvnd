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

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.jboss.fuse.mvnd.common.Environment;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnvironmentTest {

    @Test
    void prop() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            env.props("mvnd.home", "/maven/home/prop");
            Assertions.assertEquals("/maven/home/prop", DaemonParameters.systemProperty(Environment.MVND_HOME).asString());
        }
    }

    @Test
    void env() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            env.env("MVND_HOME", "/maven/home/env");
            Assertions.assertEquals("/maven/home/env", DaemonParameters.environmentVariable(Environment.MVND_HOME).asString());
        }
    }

    @Test
    void localProps() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            final Properties localProps = new Properties();
            localProps.put("mvnd.home", "/maven/home/local");
            Assertions.assertEquals(Paths.get("/maven/home/local"),
                    DaemonParameters
                            .environmentVariable(Environment.MVND_HOME)
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
            Assertions.assertEquals("/maven/home/env",
                    DaemonParameters
                            .environmentVariable(Environment.MVND_HOME)
                            .orSystemProperty()
                            .asString());
        }
    }

    @Test
    void fail() {
        try (EnvironmentResource env = new EnvironmentResource()) {
            try {
                Assertions.assertEquals("/maven/home/env",
                        DaemonParameters
                                .environmentVariable(Environment.MVND_HOME)
                                .orSystemProperty()
                                .orFail()
                                .asString());
                Assertions.fail("IllegalStateException expected");
            } catch (IllegalStateException e) {
                Assertions.assertEquals(
                        "Could not get value for Environment.MVND_HOME from any of the following sources: environment variable MVND_HOME, system property mvnd.home",
                        e.getMessage());
            }
        }
    }

    @Test
    void cygwin() {
        Assertions.assertEquals("C:\\jdk-11.0.2\\", Environment.cygpath("/cygdrive/c/jdk-11.0.2/"));
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
