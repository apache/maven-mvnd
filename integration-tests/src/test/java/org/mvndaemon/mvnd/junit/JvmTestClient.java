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
package org.mvndaemon.mvnd.junit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.client.DefaultClient;
import org.mvndaemon.mvnd.client.ExecutionResult;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.logging.ClientOutput;

import static org.mvndaemon.mvnd.junit.TestUtils.augmentArgs;

public class JvmTestClient extends DefaultClient {

    private final DaemonParameters parameters;

    public JvmTestClient(DaemonParameters parameters) {
        super(parameters);
        this.parameters = parameters;
    }

    @Override
    public ExecutionResult execute(ClientOutput output, List<String> argv) {
        setMultiModuleProjectDirectory(argv);
        Map<String, String> prevState = setSystemPropertiesFromCommandLine(argv);
        try {
            argv = new ArrayList<>(argv);
            if (parameters instanceof TestParameters && ((TestParameters) parameters).isNoTransferProgress()) {
                argv.add("-ntp");
            }
            final ExecutionResult delegate = super.execute(output, augmentArgs(argv));
            if (output instanceof TestClientOutput) {
                return new JvmTestResult(delegate, ((TestClientOutput) output).messagesToString());
            }
            return delegate;
        } finally {
            prevState.entrySet().forEach(entry -> {
                if (entry.getValue() == null) {
                    System.clearProperty(entry.getKey());
                } else {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            });
        }
    }

    private void setMultiModuleProjectDirectory(List<String> args) {
        // Specific parameters
        Path dir;
        if (Environment.MAVEN_FILE.hasCommandLineOption(args)) {
            dir = parameters.userDir().resolve(Environment.MAVEN_FILE.getCommandLineOption(args));
            if (Files.isRegularFile(dir)) {
                dir = dir.getParent();
            }
            dir = dir.normalize();
        } else {
            dir = parameters.userDir();
        }
        System.setProperty(
                Environment.MAVEN_MULTIMODULE_PROJECT_DIRECTORY.getProperty(),
                parameters.multiModuleProjectDirectory(dir).toString());
    }

    public static class JvmTestResult implements ExecutionResult {

        private final ExecutionResult delegate;
        private final List<String> log;

        public JvmTestResult(ExecutionResult delegate, List<String> log) {
            this.delegate = delegate;
            this.log = log;
        }

        @Override
        public JvmTestResult assertFailure() {
            try {
                delegate.assertFailure();
            } catch (AssertionError e) {
                final StringBuilder sb = new StringBuilder(e.getMessage());
                sb.append("\n--- received messages start ---");
                synchronized (log) {
                    log.forEach(s -> sb.append('\n').append(s));
                }
                sb.append("\n--- received messages end ---");
                throw new AssertionError(sb.toString(), e);
            }
            return this;
        }

        @Override
        public JvmTestResult assertSuccess() {
            try {
                delegate.assertSuccess();
            } catch (AssertionError e) {
                final StringBuilder sb = new StringBuilder(e.getMessage());
                sb.append("\n--- received messages start ---");
                synchronized (log) {
                    log.forEach(s -> sb.append('\n').append(s));
                }
                sb.append("\n--- received messages end ---");
                throw new AssertionError(sb.toString(), e);
            }
            return this;
        }

        @Override
        public int getExitCode() {
            return delegate.getExitCode();
        }

        @Override
        public boolean isSuccess() {
            return delegate.isSuccess();
        }
    }
}
