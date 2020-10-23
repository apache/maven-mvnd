/*
 * Copyright 2020 the original author or authors.
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
package org.jboss.fuse.mvnd.common.logging;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Display;

/**
 * A terminal {@link ClientOutput} based on JLine.
 */
public class TerminalOutput implements ClientOutput {

    public static final int CTRL_L = 'L' & 0x1f;

    public static final int CTRL_M = 'M' & 0x1f;

    private final BlockingQueue<Event> queue;
    private final Terminal terminal;
    private final Display display;
    private final LinkedHashMap<String, Project> projects = new LinkedHashMap<>();
    private final ClientLog log;
    private final Thread worker;
    private final Thread reader;
    private volatile Exception exception;
    private volatile boolean closing;
    private final CountDownLatch closed = new CountDownLatch(1);
    private int linesPerProject = 0;
    private boolean displayDone = false;

    private final long start;
    private String name;
    private int totalProjects;
    private int doneProjects;
    private int usedCores;

    enum EventType {
        BUILD,
        PROJECT_STATE,
        PROJECT_FINISHED,
        LOG,
        ERROR,
        END_OF_STREAM,
        INPUT,
        KEEP_ALIVE
    }

    static class Event {
        public final EventType type;
        public final String projectId;
        public final String message;

        public Event(EventType type, String projectId, String message) {
            this.type = type;
            this.projectId = projectId;
            this.message = message;
        }
    }

    static class Project {
        final String id;
        String status;
        final List<String> log = new ArrayList<>();

        public Project(String id) {
            this.id = id;
        }
    }

    public TerminalOutput(Path logFile) throws IOException {
        this.start = System.currentTimeMillis();
        this.queue = new LinkedBlockingDeque<>();
        this.terminal = TerminalBuilder.terminal();
        terminal.enterRawMode();
        this.display = new Display(terminal, false);
        this.log = logFile == null ? new MessageCollector() : new FileLog(logFile);
        final Thread w = new Thread(this::displayLoop);
        w.start();
        this.worker = w;
        final Thread r = new Thread(this::readInputLoop);
        r.start();
        this.reader = r;
    }

    public void startBuild(String name, int projects, int cores) {
        this.name = name;
        this.totalProjects = projects;
        this.doneProjects = 0;
        this.usedCores = cores;
    }

    public void projectStateChanged(String projectId, String task) {
        try {
            queue.put(new Event(EventType.PROJECT_STATE, projectId, task));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void projectFinished(String projectId) {
        try {
            queue.put(new Event(EventType.PROJECT_FINISHED, projectId, null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void accept(String projectId, String message) {
        try {
            if (closing) {
                closed.await();
                System.err.println(message);
            } else {
                queue.put(new Event(EventType.LOG, projectId, message));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void error(String message, String className, String stackTrace) {
        final String msg;
        if ("org.apache.commons.cli.UnrecognizedOptionException".equals(className)) {
            msg = "Unable to parse command line options: " + message;
        } else {
            msg = className + ": " + message;
        }
        try {
            queue.put(new Event(EventType.ERROR, null, msg));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void keepAlive() {
        try {
            queue.put(new Event(EventType.KEEP_ALIVE, null, null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void readInputLoop() {
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

    void displayLoop() {
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
                        log.close();
                        terminal.flush();
                        return;
                    }
                    case LOG: {
                        if (entry.projectId != null) {
                            Project prj = projects.computeIfAbsent(entry.projectId, Project::new);
                            prj.log.add(entry.message);
                        } else {
                            log.accept(entry.message);
                        }
                        break;
                    }
                    case ERROR: {
                        projects.values().stream().flatMap(p -> p.log.stream()).forEach(log);
                        clearDisplay();
                        log.close();
                        final AttributedStyle s = new AttributedStyle().bold().foreground(AttributedStyle.RED);
                        new AttributedString(entry.message, s).println(terminal);
                        terminal.flush();
                        return;
                    }
                    case PROJECT_STATE: {
                        Project prj = projects.computeIfAbsent(entry.projectId, Project::new);
                        prj.status = entry.message;
                        break;
                    }
                    case PROJECT_FINISHED: {
                        Project prj = projects.remove(entry.projectId);
                        if (prj != null) {
                            prj.log.forEach(log);
                        }
                        doneProjects++;
                        displayDone();
                        break;
                    }
                    case INPUT:
                        switch (entry.message.charAt(0)) {
                        case '+':
                            linesPerProject = Math.min(10, linesPerProject + 1);
                            break;
                        case '-':
                            linesPerProject = Math.max(0, linesPerProject - 1);
                            break;
                        case CTRL_L:
                            display.update(List.of(), 0);
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
        closed.countDown();
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
        int dispLines = rows - 1; // for the "Building..." line
        dispLines--; // there's a bug which sometimes make the cursor goes one line below, so keep one more line empty at the end
        if (projects.size() <= dispLines) {
            if (name != null) {
                AttributedStringBuilder asb = new AttributedStringBuilder();
                asb.append("Building ");
                asb.style(AttributedStyle.BOLD);
                asb.append(name);
                asb.style(AttributedStyle.DEFAULT);

                StringBuilder statusLine = new StringBuilder(64);
                statusLine.append("  threads: ").append(usedCores);

                statusLine.append("  time: ");
                long sec = (System.currentTimeMillis() - this.start) / 1000;
                if (sec > 60) {
                    statusLine.append(sec / 60).append('m').append(String.valueOf(sec % 60)).append('s');
                } else {
                    statusLine.append(sec).append('s');
                }

                if (totalProjects > 0) {
                    statusLine.append("  progress: ").append(doneProjects).append('/').append(totalProjects).append(' ')
                            .append(doneProjects * 100 / totalProjects).append('%');
                }
                lines.add(asb.append(statusLine.toString()).toAttributedString());
            }
            int remLogLines = dispLines - projects.size();
            for (Project prj : projects.values()) {
                String str = prj.status != null ? prj.status : ":" + prj.id + ":<unknown>";
                int cs = str.indexOf(':');
                int ce = cs >= 0 ? str.indexOf(':', cs + 1) : -1;
                if (ce > 0) {
                    AttributedStringBuilder asb = new AttributedStringBuilder();
                    asb.append(str, 0, cs);
                    asb.style(AttributedStyle.BOLD);
                    asb.append(str, cs, ce);
                    asb.style(AttributedStyle.DEFAULT);
                    asb.append(str, ce, str.length());
                    lines.add(asb.toAttributedString());
                } else {
                    lines.add(AttributedString.fromAnsi(str));
                }
                // get the last lines of the project log, taking multi-line logs into account
                int nb = Math.min(remLogLines, linesPerProject);
                List<AttributedString> logs = lastN(prj.log, nb).stream()
                        .flatMap(s -> AttributedString.fromAnsi(s).columnSplitLength(Integer.MAX_VALUE).stream())
                        .map(s -> concat("   ", s))
                        .collect(lastN(nb));
                lines.addAll(logs);
                remLogLines -= logs.size();
            }
        } else {
            lines.add(new AttributedString("Building... (" + (projects.size() - dispLines) + " more)"));
            lines.addAll(projects.values().stream()
                    .map(prj -> AttributedString.fromAnsi(prj.status != null ? prj.status : "<unknown>"))
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
            if (n > 0) {
                if (acc.size() == n)
                    acc.pollFirst();
                acc.add(t);
            }
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

    /**
     * A closeable string message consumer.
     */
    interface ClientLog extends Consumer<String> {

        void accept(String message);

        void flush() throws IOException;

        void close() throws IOException;
    }

    /**
     * A {@link ClientLog} backed by a file.
     */
    static class FileLog implements ClientLog {

        private final Writer out;
        private final Path logFile;

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
     * A {@link ClientLog} that first collects all incoming messages in a {@link List} and outputs them to a JLine
     * {@link Terminal} upon {@link #close()}.
     */
    class MessageCollector implements ClientLog {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void accept(String message) {
            messages.add(message);
        }

        @Override
        public void flush() {
            clearDisplay();
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
