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
package org.mvndaemon.mvnd.daemon;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.jline.MessageUtils;
import org.apache.maven.project.MavenProject;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.common.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSummaryExecutionListenerTest {

    private final List<Message> queue = new ArrayList<>();
    private final ClientDispatcher dispatcher = new ClientDispatcher(queue);

    private static Terminal terminal;

    /** Maven only styles messages through an installed non-dumb terminal, as it has during a real build. */
    @BeforeAll
    static void installTerminal() throws IOException {
        terminal = TerminalBuilder.builder()
                .system(false)
                .type("xterm-256color")
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .build();
        MessageUtils.systemInstall(terminal);
    }

    /** Not {@code systemUninstall()}: it requires an AnsiConsole install that {@code systemInstall(Terminal)} skips. */
    @AfterAll
    static void closeTerminal() throws IOException {
        terminal.close();
        MessageUtils.setColorEnabled(true);
    }

    /** A failed and a flaky test, so the summary contains INFO, WARNING and ERROR lines. */
    private void recordFailedAndFlakyTest() {
        dispatcher
                .getTestSummary()
                .record(
                        "app",
                        1,
                        3,
                        1,
                        0,
                        0,
                        0,
                        1,
                        List.of("FooTest#flaky\n  Run 1: boom\n  Run 2: PASS"),
                        List.of("FooTest#bad"),
                        List.of());
    }

    private List<String> logLines() {
        return queue.stream()
                .filter(m -> m.getType() == Message.BUILD_LOG_MESSAGE)
                .map(m -> ((Message.StringMessage) m).getMessage())
                .collect(Collectors.toList());
    }

    private static String failure(String text) {
        return MessageUtils.builder().failure(text).toString();
    }

    private static String warning(String text) {
        return MessageUtils.builder().warning(text).toString();
    }

    @Test
    void summaryKeepsSeverityColorsWhenColorIsEnabled() {
        MessageUtils.setColorEnabled(true);
        recordFailedAndFlakyTest();

        ExecutionListener listener =
                TestSummaryExecutionListener.decorate(recordingListener(new ArrayList<>()), dispatcher);
        listener.sessionEnded(event());

        // Surefire colors these lines by severity; losing that makes a failed build's summary look like plain INFO.
        assertEquals(
                List.of(
                        "",
                        "Results:",
                        "",
                        failure("Failures: "),
                        failure("  app FooTest#bad"),
                        warning("Flakes: "),
                        warning("  app FooTest#flaky"),
                        failure("    Run 1: boom"),
                        "    Run 2: PASS",
                        "",
                        failure("Tests run: 3, Failures: 1, Errors: 0, Skipped: 0, Flakes: 1"),
                        ""),
                logLines());
        assertTrue(failure("x").contains("\u001b["), "failure lines must actually carry ANSI styling");
        assertFalse(failure("x").equals(warning("x")), "failure and warning lines must be distinguishable");
    }

    @Test
    void summaryIsPlainTextWhenColorIsDisabled() {
        // --color=never and --log-file both resolve to this; ANSI codes would corrupt the log file.
        MessageUtils.setColorEnabled(false);
        recordFailedAndFlakyTest();

        TestSummaryExecutionListener.decorate(recordingListener(new ArrayList<>()), dispatcher)
                .sessionEnded(event());

        assertTrue(logLines().contains("Failures: "), "expected unstyled header, got: " + logLines());
        assertTrue(logLines().stream().noneMatch(l -> l.contains("\u001b")), "unexpected ANSI in: " + logLines());
    }

    @Test
    void summaryIsSentBeforeTheReactorSummary() {
        recordFailedAndFlakyTest();
        List<Integer> linesSentWhenDelegateEnded = new ArrayList<>();
        ExecutionListener delegate = (ExecutionListener) Proxy.newProxyInstance(
                ExecutionListener.class.getClassLoader(),
                new Class<?>[] {ExecutionListener.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sessionEnded")) {
                        linesSentWhenDelegateEnded.add(logLines().size());
                    }
                    return null;
                });

        TestSummaryExecutionListener.decorate(delegate, dispatcher).sessionEnded(event());

        // The delegate prints Maven's Reactor Summary in sessionEnded, and the test summary has to come first.
        assertEquals(List.of(logLines().size()), linesSentWhenDelegateEnded);
        assertFalse(logLines().isEmpty());
    }

    @Test
    void everyListenerMethodReachesTheDelegate() throws Exception {
        // Each event dropped here silently breaks Maven's own output, for example the Reactor Summary or the
        // build-event stream, so a method added to ExecutionListener must be forwarded too.
        List<String> delegated = new ArrayList<>();
        ExecutionListener listener = TestSummaryExecutionListener.decorate(recordingListener(delegated), dispatcher);

        Set<String> expected = new TreeSet<>();
        for (Method method : ExecutionListener.class.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                expected.add(method.getName());
                method.invoke(listener, event());
            }
        }

        assertEquals(expected, new TreeSet<>(delegated));
        assertEquals(expected.size(), delegated.size(), "each event must be forwarded exactly once: " + delegated);
    }

    private static ExecutionListener recordingListener(List<String> calls) {
        return (ExecutionListener) Proxy.newProxyInstance(
                ExecutionListener.class.getClassLoader(),
                new Class<?>[] {ExecutionListener.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    return null;
                });
    }

    private static ExecutionEvent event() {
        return (ExecutionEvent) Proxy.newProxyInstance(
                ExecutionEvent.class.getClassLoader(),
                new Class<?>[] {ExecutionEvent.class},
                (proxy, method, args) -> method.getName().equals("getProject") ? new MavenProject() : null);
    }
}
