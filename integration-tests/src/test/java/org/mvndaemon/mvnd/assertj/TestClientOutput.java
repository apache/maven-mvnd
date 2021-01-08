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
package org.mvndaemon.mvnd.assertj;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.common.logging.ClientOutput;

public class TestClientOutput implements ClientOutput {
    private final List<Message> messages = new ArrayList<>();
    protected Consumer<Message> daemonDispatch;

    @Override
    public void close() throws Exception {
    }

    @Override
    public void setDaemonId(String daemonId) {
    }

    @Override
    public void setDaemonDispatch(Consumer<Message> daemonDispatch) {
        this.daemonDispatch = daemonDispatch;
    }

    @Override
    public void setDaemonReceive(Consumer<Message> sink) {
    }

    @Override
    public void accept(Message message) {
        messages.add(message);
    }

    @Override
    public void accept(List<Message> messages) {
        for (Message message : messages) {
            accept(message);
        }
    }

    @Override
    public void describeTerminal() {
        accept(Message.display("Test terminal"));
    }

    @Override
    public int getTerminalWidth() {
        return 74;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void assertContainsMatchingSubsequence(String... patterns) {
        Assertions.assertThat(messagesToString()).is(new MatchInOrderAmongOthers<>(patterns));
    }

    public List<String> messagesToString() {
        return messages.stream().map(m -> m.toString()).collect(Collectors.toList());
    }

}
