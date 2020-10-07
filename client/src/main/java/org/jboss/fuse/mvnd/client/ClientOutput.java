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

import java.io.Flushable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.jboss.fuse.mvnd.common.Message.BuildException;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sink for various kinds of events sent by the daemon.
 */
public interface ClientOutput extends AutoCloseable {

    int CTRL_L = 'L' & 0x1f;

    int CTRL_M = 'M' & 0x1f;

    public void projectStateChanged(String projectId, String display);

    public void projectFinished(String projectId);

    public void accept(String projectId, String message);

    public void error(BuildException m);

    enum EventType {
        PROJECT_STATUS,
        LOG,
        ERROR,
        END_OF_STREAM,
        INPUT
    }

    class Event {
        public final EventType type;
        public final String projectId;
        public final String message;

        public Event(EventType type, String projectId, String message) {
            this.type = type;
            this.projectId = projectId;
            this.message = message;
        }
    }

    class Project {
        String status;
        final List<String> log = new ArrayList<>();
    }

    /**
     * A terminal {@link ClientOutput} based on JLine.
     */
    static class TerminalOutput implements ClientOutput {
        private static final Logger LOGGER = LoggerFactory.getLogger(TerminalOutput.class);
        private final TerminalUpdater updater;
        private final BlockingQueue<Event> queue;

        public TerminalOutput(Path logFile) throws IOException {
            this.queue = new LinkedBlockingDeque<>();
            this.updater = new TerminalUpdater(queue, logFile);
        }

        public void projectStateChanged(String projectId, String task) {
            try {
                queue.put(new Event(EventType.PROJECT_STATUS, projectId, task));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public void projectFinished(String projectId) {
            try {
                queue.put(new Event(EventType.PROJECT_STATUS, projectId, null));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void accept(String projectId, String message) {
            try {
                queue.put(new Event(EventType.LOG, projectId, message));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() throws Exception {
            updater.close();
        }

        @Override
        public void error(BuildException error) {
            final String msg;
            if ("org.apache.commons.cli.UnrecognizedOptionException".equals(error.getClassName())) {
                msg = "Unable to parse command line options: " + error.getMessage();
            } else {
                msg = error.getClassName() + ": " + error.getMessage();
            }
            try {
                queue.put(new Event(EventType.ERROR, null, msg));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        static class TerminalUpdater implements AutoCloseable {
            private final BlockingQueue<Event> queue;
            private final Terminal terminal;
            private final Display display;
            private final LinkedHashMap<String, Project> projects = new LinkedHashMap<>();
            private final Log log;
            private final Thread worker;
            private final Thread reader;
            private volatile Exception exception;
            private volatile boolean closing;
            private int linesPerProject = 0;
            private boolean displayDone = false;

            public TerminalUpdater(BlockingQueue<Event> queue, Path logFile) throws IOException {
                super();
                this.terminal = TerminalBuilder.terminal();
                terminal.enterRawMode();
                this.display = new Display(terminal, false);
                this.log = logFile == null ? new ClientOutput.Log.MessageCollector(terminal, this::clearDisplay)
                        : new ClientOutput.Log.FileLog(logFile);
                this.queue = queue;
                final Thread w = new Thread(this::run);
                w.start();
                this.worker = w;
                final Thread r = new Thread(this::read);
                r.start();
                this.reader = r;
            }

            void read() {
                try {
                    while (!closing) {
                        int c = terminal.reader().read(10);
                        if (c == -1) {
                            break;
                        }
                        if (c == '+' || c == '-' || c == CTRL_L || c == CTRL_M) {
                            queue.add(new Event(EventType.INPUT, null, Character.toString(c)));
                        }
                    }
                } catch (InterruptedIOException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    this.exception = e;
                }
            }

            void run() {
                final List<Event> entries = new ArrayList<>();

                while (true) {
                    try {
                        entries.add(queue.take());
                        queue.drainTo(entries);
                       for (Event entry : entries) {
                            switch (entry.type) {
                            case END_OF_STREAM: {
                                projects.values().stream().flatMap(p -> p.log.stream()).forEach(log);
                                clearDisplay();
                                LOGGER.debug("Done receiving, printing log");
                                log.close();
                                LOGGER.debug("Done !");
                                terminal.flush();
                                return;
                            }
                            case LOG: {
                                if (entry.projectId != null) {
                                    Project prj = projects.computeIfAbsent(entry.projectId, p -> new Project());
                                    prj.log.add(entry.message);
                                } else {
                                    log.accept(entry.message);
                                }
                                break;
                            }
                            case ERROR: {
                                projects.values().stream().flatMap(p -> p.log.stream()).forEach(log);
                                clearDisplay();
                                final AttributedStyle s = new AttributedStyle().bold().foreground(AttributedStyle.RED);
                                terminal.writer().println(new AttributedString(entry.message, s).toAnsi());
                                terminal.flush();
                                return;
                            }
                            case PROJECT_STATUS:
                                if (entry.message != null) {
                                    Project prj = projects.computeIfAbsent(entry.projectId, p -> new Project());
                                    prj.status = entry.message;
                                } else {
                                    Project prj = projects.remove(entry.projectId);
                                    if (prj != null) {
                                        prj.log.forEach(log);
                                    }
                                    displayDone();
                                }
                                break;
                            case INPUT:
                                switch (entry.message.charAt(0)) {
                                case '+':
                                    linesPerProject = Math.min(10, linesPerProject + 1);
                                    break;
                                case '-':
                                    linesPerProject = Math.max(0, linesPerProject - 1);
                                    break;
                                case CTRL_L:
                                    display.reset();
                                    break;
                                case CTRL_M:
                                    displayDone = !displayDone;
                                    displayDone();
                                    break;
                                }
                                break;
                            }
                        }
                        entries.clear();
                        update();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        this.exception = e;
                    }
                }
            }

            private void clearDisplay() {
                display.update(Collections.emptyList(), 0);
            }

            private void displayDone() throws IOException {
                if (displayDone) {
                    log.flush();
                }
            }

            @Override
            public void close() throws Exception {
                closing = true;
                reader.interrupt();
                queue.put(new Event(EventType.END_OF_STREAM, null, null));
                worker.join();
                reader.join();
                terminal.close();
                if (exception != null) {
                    throw exception;
                }
            }

            private void update() {
                // no need to refresh the display at every single step
                final Size size = terminal.getSize();
                final int rows = size.getRows();
                final int cols = size.getColumns();
                display.resize(rows, size.getColumns());
                if (rows <= 0) {
                    clearDisplay();
                    return;
                }
                final List<AttributedString> lines = new ArrayList<>(rows);
                final int dispLines = rows - 1;
                if (projects.size() <= dispLines) {
                    lines.add(new AttributedString("Building..."));
                    int remLogLines = dispLines - projects.size();
                    for (Project prj : projects.values()) {
                        lines.add(AttributedString.fromAnsi(prj.status));
                        // get the last lines of the project log, taking multi-line logs into account
                        List<AttributedString> logs = lastN(prj.log, linesPerProject).stream()
                                .flatMap(s -> AttributedString.fromAnsi(s).columnSplitLength(Integer.MAX_VALUE).stream())
                                .map(s -> concat("   ", s))
                                .collect(lastN(Math.min(remLogLines, linesPerProject)));
                        lines.addAll(logs);
                        remLogLines -= logs.size();
                    }
                } else {
                    lines.add(new AttributedString("Building... (" + (projects.size() - dispLines) + " more)"));
                    lines.addAll(projects.values().stream()
                            .map(prj -> AttributedString.fromAnsi(prj.status))
                            .collect(lastN(dispLines)));
                }
                List<AttributedString> trimmed = lines.stream()
                        .map(s -> s.columnSubSequence(0, cols))
                        .collect(Collectors.toList());
                display.update(trimmed, -1);
            }

            private static <T> List<T> lastN(List<T> list, int n) {
                return list.subList(Math.max(0, list.size() - n), list.size());
            }

            private static <T> Collector<T, ?, List<T>> lastN(int n) {
                return Collector.<T, Deque<T>, List<T>> of(ArrayDeque::new, (acc, t) -> {
                    if (acc.size() == n)
                        acc.pollFirst();
                    acc.add(t);
                }, (acc1, acc2) -> {
                    while (acc2.size() < n && !acc1.isEmpty()) {
                        acc2.addFirst(acc1.pollLast());
                    }
                    return acc2;
                }, ArrayList::new);
            }

            private static AttributedString concat(String s1, AttributedString s2) {
                AttributedStringBuilder asb = new AttributedStringBuilder();
                asb.append(s1);
                asb.append(s2);
                return asb.toAttributedString();
            }

        }

    }

    /**
     * A closeable string message consumer.
     */
    interface Log extends Consumer<String>, Flushable, AutoCloseable {

        /**
         * A {@link Log} backed by a file.
         */
        public static class FileLog implements Log {

            private final Writer out;
            private Path logFile;

            public FileLog(Path logFile) throws IOException {
                super();
                this.out = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8);
                this.logFile = logFile;
            }

            @Override
            public void accept(String message) {
                try {
                    out.write(message);
                    out.write('\n');
                } catch (IOException e) {
                    throw new RuntimeException("Could not write to " + logFile, e);
                }
            }

            @Override
            public void flush() throws IOException {
                out.flush();
            }

            @Override
            public void close() throws IOException {
                out.close();
            }

        }

        /**
         * A {@link Log} that first collects all incoming messages in a {@link List} and outputs them to a JLine
         * {@link Terminal} upon {@link #close()}.
         */
        public static class MessageCollector implements Log {

            private final List<String> messages = new ArrayList<>();
            private final Terminal terminal;
            private final Runnable clear;

            public MessageCollector(Terminal terminal, Runnable clear) {
                super();
                this.terminal = terminal;
                this.clear = clear;
            }

            @Override
            public void accept(String message) {
                messages.add(message);
            }

            @Override
            public void flush() {
                clear.run();
                messages.forEach(terminal.writer()::println);
                messages.clear();
                terminal.flush();
            }

            @Override
            public void close() {
                flush();
            }

        }
    }

}
