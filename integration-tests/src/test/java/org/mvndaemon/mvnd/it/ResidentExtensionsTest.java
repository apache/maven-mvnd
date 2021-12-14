/*
 * Copyright 2021 the original author or authors.
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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.MatchInOrderAmongOthers;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.junit.MvndTest;

@MvndTest(projectDir = "src/test/projects/resident-extensions")
public class ResidentExtensionsTest {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void verify() throws IOException, InterruptedException {
        TestClientOutput o1 = new TestClientOutput();
        client.execute(o1, "verify", "-e", "-B", "-f", "project1/pom.xml").assertSuccess();
        Assertions.assertThat(o1.messagesToString()).is(new MatchInOrderAmongOthers<>("Writing maven timeline"));

        TestClientOutput o2 = new TestClientOutput();
        client.execute(o2, "verify", "-e", "-B", "-f", "project2/pom.xml").assertSuccess();
        Assertions.assertThat(o2.messagesToString()).isNot(new MatchInOrderAmongOthers<>("Writing maven timeline"));
    }

}
