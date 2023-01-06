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
package org.mvndaemon.mvnd.it;

import javax.inject.Inject;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.junit.MvndNativeTest;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MaxHeapNativeIT {

    static class BaseTest {

        @Inject
        Client client;

        static ListAppender<ILoggingEvent> appender = new ListAppender<>();

        @BeforeAll
        static void setup() {
            Logger logger = (Logger) LoggerFactory.getLogger("org.mvndaemon.mvnd.client.DaemonConnector");
            logger.setLevel(Level.DEBUG);
            logger.addAppender(appender);
            appender.start();
        }

        @AfterAll
        static void tearDown() {
            Logger logger = (Logger) LoggerFactory.getLogger("org.mvndaemon.mvnd.client.DaemonConnector");
            logger.detachAppender(appender);
        }

        static String getDaemonArgs() {
            return appender.list.stream()
                    .filter(e -> e.getMessage().contains("Starting daemon process"))
                    .map(e -> e.getArgumentArray()[2].toString())
                    .findAny()
                    .orElseThrow();
        }

        @BeforeEach
        void unitSetup() {
            appender.list.clear();
        }
    }

    @MvndNativeTest(projectDir = "src/test/projects/max-heap/default-heap")
    static class DefaultConfig extends BaseTest {

        @Test
        void noXmxPassedByDefault() throws InterruptedException {
            final TestClientOutput output = new TestClientOutput();
            client.execute(
                            output,
                            "-Dmvnd.log.level=DEBUG",
                            "org.codehaus.gmaven:groovy-maven-plugin:2.1.1:execute",
                            "-Dsource=System.out.println(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments())")
                    .assertSuccess();
            String daemonArgs = getDaemonArgs();
            assertTrue(
                    !daemonArgs.contains("-Xmx") && !daemonArgs.contains("mvnd.maxHeapSize"),
                    "Args must not contain -Xmx or mvnd.maxHeapSize but is:\n" + daemonArgs);
        }
    }

    @MvndNativeTest(projectDir = "src/test/projects/max-heap/jvm-heap")
    static class JvmConfig extends BaseTest {

        @Test
        void xmxFromJvmConfig() throws InterruptedException {
            final TestClientOutput output = new TestClientOutput();
            client.execute(
                            output,
                            "-Dmvnd.log.level=DEBUG",
                            "org.codehaus.gmaven:groovy-maven-plugin:2.1.1:execute",
                            "-Dsource=System.out.println(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments())")
                    .assertSuccess();
            String daemonArgs = getDaemonArgs();
            assertTrue(
                    !daemonArgs.contains("-Xmx") && !daemonArgs.contains("mvnd.maxHeapSize"),
                    "Args must not contain -Xmx or mvnd.maxHeapSize but is:\n" + daemonArgs);
        }
    }

    @MvndNativeTest(projectDir = "src/test/projects/max-heap/mvnd-props")
    static class MvndProps extends BaseTest {

        @Test
        void xmxFromMvndProperties() throws InterruptedException {
            final TestClientOutput output = new TestClientOutput();
            client.execute(
                            output,
                            "-Dmvnd.log.level=DEBUG",
                            "org.codehaus.gmaven:groovy-maven-plugin:2.1.1:execute",
                            "-Dsource=System.out.println(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments())")
                    .assertSuccess();
            String daemonArgs = getDaemonArgs();
            assertTrue(
                    daemonArgs.contains("-Xmx130M") && daemonArgs.contains("mvnd.maxHeapSize=130M"),
                    "Args must contain -Xmx130M or mvnd.maxHeapSize=130M but is:\n" + daemonArgs);
        }
    }
}
