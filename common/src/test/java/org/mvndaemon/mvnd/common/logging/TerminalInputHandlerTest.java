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
package org.mvndaemon.mvnd.common.logging;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.common.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalInputHandlerTest {

    private final BlockingQueue<Message> dispatched = new LinkedBlockingQueue<>();
    private final BlockingQueue<Message> received = new LinkedBlockingQueue<>();
    private final ByteArrayOutputStream screen = new ByteArrayOutputStream();
    private PipedOutputStream keys;
    private Terminal terminal;
    private TerminalInputHandler inputHandler;

    @BeforeEach
    void setUp() throws Exception {
        keys = new PipedOutputStream();
        terminal = TerminalBuilder.builder()
                .system(false)
                .type("xterm")
                .streams(new PipedInputStream(keys), screen)
                .build();
        terminal.enterRawMode();
        inputHandler = new TerminalInputHandler(terminal, false);
        inputHandler.setDaemonDispatch(dispatched::add);
    }

    @AfterEach
    void tearDown() throws Exception {
        inputHandler.close();
        terminal.close();
    }

    @Test
    void ctrlCBeforeConnectingCancels() throws Exception {
        type(TerminalOutput.KEY_CTRL_C);
        assertSame(Message.BareMessage.CANCEL_BUILD_SINGLETON, dispatched.poll(5, TimeUnit.SECONDS));
    }

    @Test
    void ctrlCDuringBuildCancels() throws Exception {
        inputHandler.setDaemonReceive(received::add);
        type(TerminalOutput.KEY_CTRL_C);
        assertSame(Message.BareMessage.CANCEL_BUILD_SINGLETON, dispatched.poll(5, TimeUnit.SECONDS));
        assertNull(received.poll());
    }

    @Test
    void ctrlCInPromptCancels() throws Exception {
        inputHandler.setDaemonReceive(received::add);
        inputHandler.requestPrompt(new Message.Prompt("project", "uid", "Continue? ", false));
        long deadline = System.currentTimeMillis() + 5000;
        while (!screen.toString().contains("Continue? ") && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        type('y');
        type(TerminalOutput.KEY_CTRL_C);
        assertSame(Message.BareMessage.CANCEL_BUILD_SINGLETON, dispatched.poll(5, TimeUnit.SECONDS));
        assertNull(dispatched.poll());
        assertTrue(screen.toString().contains("Continue? y"));
    }

    @Test
    void keysTypedBeforeConnectingAreDropped() throws Exception {
        type(TerminalOutput.KEY_PLUS);
        type(TerminalOutput.KEY_CTRL_C);
        // the cancel shows that both keys have been read
        assertSame(Message.BareMessage.CANCEL_BUILD_SINGLETON, dispatched.poll(5, TimeUnit.SECONDS));

        inputHandler.setDaemonReceive(received::add);
        type(TerminalOutput.KEY_MINUS);
        Message m = received.poll(5, TimeUnit.SECONDS);
        assertEquals(Message.KEYBOARD_INPUT, m.getType());
        assertEquals("-", ((Message.StringMessage) m).getMessage());
    }

    private void type(int key) throws Exception {
        keys.write(key);
        keys.flush();
    }
}
