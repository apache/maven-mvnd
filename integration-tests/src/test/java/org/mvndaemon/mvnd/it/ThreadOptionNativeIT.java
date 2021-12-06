/*
 * Copyright 2019-2021 the original author or authors.
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
package org.mvndaemon.mvnd.it;

import java.io.IOException;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.junit.MvndNativeTest;

@MvndNativeTest(projectDir = "src/test/projects/multi-module")
public class ThreadOptionNativeIT {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void minusTSpace2() throws IOException, InterruptedException {
        final TestClientOutput output = new TestClientOutput();

        client.execute(output, "-T", "2", "verify").assertSuccess();

        output.assertContainsMatchingSubsequence("Using the SmartBuilder implementation with a thread count of 2");
    }

    @Test
    void minusT2() throws IOException, InterruptedException {
        final TestClientOutput output = new TestClientOutput();

        client.execute(output, "-T2", "verify").assertSuccess();

        output.assertContainsMatchingSubsequence("Using the SmartBuilder implementation with a thread count of 2");
    }

    @Test
    void minusThreadsSpace2() throws IOException, InterruptedException {
        final TestClientOutput output = new TestClientOutput();

        client.execute(output, "--threads", "2", "verify").assertSuccess();

        output.assertContainsMatchingSubsequence("Using the SmartBuilder implementation with a thread count of 2");
    }

    @Test
    void minusThreads2() throws IOException, InterruptedException {
        final TestClientOutput output = new TestClientOutput();

        client.execute(output, "--threads=2", "verify").assertSuccess();

        output.assertContainsMatchingSubsequence("Using the SmartBuilder implementation with a thread count of 2");
    }

    @Test
    void mvndThreads() throws IOException, InterruptedException {
        final TestClientOutput output = new TestClientOutput();

        client.execute(output, "-Dmvnd.threads=2", "verify").assertSuccess();

        output.assertContainsMatchingSubsequence("Using the SmartBuilder implementation with a thread count of 2");
    }

    protected boolean isNative() {
        return true;
    }
}
