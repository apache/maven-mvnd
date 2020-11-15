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
package org.mvndaemon.mvnd.junit;

import java.util.List;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.client.DefaultClient;
import org.mvndaemon.mvnd.client.ExecutionResult;
import org.mvndaemon.mvnd.common.logging.ClientOutput;

public class JvmTestClient extends DefaultClient {

    public JvmTestClient(DaemonParameters parameters) {
        super(parameters);
    }

    @Override
    public ExecutionResult execute(ClientOutput output, List<String> argv) {
        final ExecutionResult delegate = super.execute(output, argv);
        if (output instanceof TestClientOutput) {
            return new JvmTestResult(delegate, ((TestClientOutput) output).messagesToString());
        }
        return delegate;
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
