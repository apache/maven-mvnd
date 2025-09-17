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

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.jline.terminal.Terminal;
import org.mvndaemon.mvnd.common.Message;

/**
 * Handles terminal input in a clean, thread-safe manner using a producer-consumer pattern.
 *
 * This class is responsible for:
 * 1. Reading input from the terminal based on different types of requests:
 *    - Project input: Reading raw input for a specific project
 *    - Prompts: Handling interactive prompts with user feedback
 *    - Control keys: Monitoring for special control keys in non-dumb terminals
 *
 * 2. Managing input state through InputRequest objects which specify:
 *    - The type of input needed (project input, prompt, or control keys)
 *    - The project requiring input
 *    - How many bytes to read
 *
 * 3. Converting input to appropriate Message objects and sending them to either:
 *    - daemonDispatch: for prompt responses
 *    - daemonReceive: for project input and control keys
 *
 * The class detects end-of-stream conditions (EOF) and communicates them back through
 * the message system, which is crucial for handling piped input (e.g., cat file | mvnd ...).
 *
 * Input handling differs based on terminal type:
 * - Normal terminals: Handle all input types including control keys
 * - Dumb terminals: Only handle project input and prompts, ignore control keys.
 */
public class TerminalInputHandler implements AutoCloseable {
    private final Terminal terminal;
    private final BlockingQueue<InputRequest> inputRequests;
    private volatile boolean closing;
    private final Thread inputThread;
    private final boolean dumb;
    private volatile int maxThreads;

    private volatile Consumer<Message> daemonDispatch;
    private volatile Consumer<Message> daemonReceive;

    private static class InputRequest {
        final String projectId; // null for control keys
        final Message.Prompt prompt; // non-null only for prompt requests
        final boolean isControlKey; // true for control key listening
        final int bytesToRead; // max number of bytes to read

        private InputRequest(String projectId, Message.Prompt prompt, boolean isControlKey, int bytesToRead) {
            this.projectId = projectId;
            this.prompt = prompt;
            this.isControlKey = isControlKey;
            this.bytesToRead = bytesToRead;
        }

        static InputRequest forProject(String projectId, int bytesToRead) {
            return new InputRequest(projectId, null, false, bytesToRead);
        }

        static InputRequest forPrompt(Message.Prompt prompt) {
            return new InputRequest(prompt.getProjectId(), prompt, false, 0);
        }

        static InputRequest forControlKeys() {
            return new InputRequest(null, null, true, 0);
        }
    }

    public TerminalInputHandler(Terminal terminal, boolean dumb) {
        this.terminal = terminal;
        this.inputRequests = new LinkedBlockingQueue<>();
        this.dumb = dumb;

        // Always create input thread as we always need to handle prompts and project input
        this.inputThread = new Thread(() -> {
            try {
                while (!closing) {
                    InputRequest request = inputRequests.poll(10, TimeUnit.MILLISECONDS);
                    if (request == null) {
                        // No active request
                        if (!dumb) {
                            // Only listen for control keys in non-dumb mode
                            handleControlKeys();
                        }
                    } else if (request.prompt != null) {
                        // Always handle prompts
                        handlePrompt(request.prompt);
                    } else if (request.projectId != null) {
                        // Always handle project input
                        handleProjectInput(request.projectId, request.bytesToRead);
                    } else if (!dumb && request.isControlKey) {
                        // Only handle control keys in non-dumb mode
                        handleControlKeys();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // Handle terminal IO exception
            }
        });
        inputThread.setDaemon(true);
        inputThread.start();
    }

    private void handleProjectInput(String projectId, int bytesToRead) throws IOException {
        if (daemonReceive == null) {
            return;
        }
        char[] buf = bytesToRead > 0 ? new char[bytesToRead] : new char[8192];
        int idx = 0;
        int timeout = 10; // Initial timeout for first read

        while ((bytesToRead < 0 || idx < bytesToRead) && idx < buf.length) {
            int c = terminal.reader().read(timeout);
            if (c < 0) {
                // End of stream reached
                daemonReceive.accept(Message.inputEof());
                break;
            }
            buf[idx++] = (char) c;
            timeout = idx > 0 ? 1 : 10; // Shorter timeout after first char
        }

        if (idx > 0) {
            String data = String.valueOf(buf, 0, idx);
            daemonReceive.accept(Message.inputResponse(data));
        }
    }

    private void handleControlKeys() throws IOException {
        if (daemonReceive == null) {
            return;
        }
        int c = terminal.reader().read(10);
        if (c != -1 && isControlKey(c)) {
            daemonReceive.accept(Message.keyboardInput((char) c));
        }
    }

    private void handlePrompt(Message.Prompt prompt) throws IOException {
        if (daemonDispatch == null) {
            return;
        }
        if (prompt.getMessage() != null) {
            String msg = formatPromptMessage(prompt);
            terminal.writer().print(msg);
        }
        terminal.flush();

        StringBuilder sb = new StringBuilder();
        while (true) {
            int c = terminal.reader().read();
            if (c < 0) {
                break;
            } else if (c == '\n' || c == '\r') {
                terminal.writer().println();
                terminal.writer().flush();
                daemonDispatch.accept(prompt.response(sb.toString()));
                break;
            } else if (c == 127) { // Backspace
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 1);
                    terminal.writer().write("\b \b");
                    terminal.writer().flush();
                }
            } else {
                terminal.writer().print((char) c);
                terminal.writer().flush();
                sb.append((char) c);
            }
        }
        // After prompt is handled, go back to control key listening only if not dumb
        if (!dumb) {
            inputRequests.offer(InputRequest.forControlKeys());
        }
    }

    private boolean isControlKey(int c) {
        return c == TerminalOutput.KEY_PLUS
                || c == TerminalOutput.KEY_MINUS
                || c == TerminalOutput.KEY_CTRL_L
                || c == TerminalOutput.KEY_CTRL_M
                || c == TerminalOutput.KEY_CTRL_B;
    }

    private String formatPromptMessage(Message.Prompt prompt) {
        return (maxThreads > 1)
                ? String.format("[%s] %s", prompt.getProjectId(), prompt.getMessage())
                : prompt.getMessage();
    }

    public void setDaemonDispatch(Consumer<Message> daemonDispatch) {
        this.daemonDispatch = daemonDispatch;
    }

    public void setDaemonReceive(Consumer<Message> daemonReceive) {
        this.daemonReceive = daemonReceive;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public void requestProjectInput(String projectId, int bytesToRead) {
        inputRequests.clear(); // Clear any pending requests
        inputRequests.offer(InputRequest.forProject(projectId, bytesToRead));
    }

    public void requestPrompt(Message.Prompt prompt) {
        inputRequests.clear(); // Clear any pending requests
        inputRequests.offer(InputRequest.forPrompt(prompt));
    }

    @Override
    public void close() {
        closing = true;
        if (inputThread != null) {
            inputThread.interrupt();
        }
    }
}
