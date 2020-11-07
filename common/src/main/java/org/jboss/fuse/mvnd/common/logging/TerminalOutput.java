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
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.AbstractPosixTerminal;
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
    private final long start;
    private final ReadWriteLock readInput = new ReentrantReadWriteLock();

    private volatile String name;
    private volatile int totalProjects;
    private volatile int maxThreads;

    private int linesPerProject = 0; // read/written only by the displayLoop
    private int doneProjects = 0; // read/written only by the displayLoop
    private String buildStatus; // read/written only by the displayLoop
    private boolean displayDone = false; // read/written only by the displayLoop

    enum EventType {
        BUILD_STATUS,
        PROJECT_STATE,
        PROJECT_FINISHED,
        LOG,
        ERROR,
        END_OF_STREAM,
        INPUT,
        KEEP_ALIVE,
        DISPLAY,
        PROMPT,
        PROMPT_PASSWORD
    }

    static class Event {
        public static final Event KEEP_ALIVE = new Event(EventType.KEEP_ALIVE, null, null);
        public final EventType type;
        public final String projectId;
        public final String message;
        public final SynchronousQueue<String> response;

        public Event(EventType type, String projectId, String message) {
            this(type, projectId, message, null);
        }

        public Event(EventType type, String projectId, String message, SynchronousQueue<String> response) {
            this.type = type;
            this.projectId = projectId;
            this.message = message;
            this.response = response;
        }
    }

    /**
     * {@link Project} is owned by the display loop thread and is accessed only from there. Therefore it does not need
     * to be immutable.
     */
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
        this.maxThreads = cores;
        try {
            queue.put(Event.KEEP_ALIVE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
            queue.put(Event.KEEP_ALIVE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void buildStatus(String status) {
        try {
            queue.put(new Event(EventType.BUILD_STATUS, null, status));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void display(String projectId, String message) {
        try {
            queue.put(new Event(EventType.DISPLAY, projectId, message));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String prompt(String projectId, String message, boolean password) {
        String response = null;
        try {
            SynchronousQueue<String> sq = new SynchronousQueue<>();
            queue.put(new Event(password ? EventType.PROMPT_PASSWORD : EventType.PROMPT, projectId, message, sq));
            response = sq.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return response;
    }

    @Override
    public void describeTerminal() {
        StringBuilder sb = new StringBuilder();
        sb.append("Terminal: ").append(terminal != null ? terminal.getClass().getName() : null);
        if (terminal instanceof AbstractPosixTerminal) {
            sb.append(" with pty ").append(((AbstractPosixTerminal) terminal).getPty().getClass().getName());
        }
        this.accept(null, sb.toString());
    }

    void readInputLoop() {
        try {
            while (!closing) {
                if (readInput.readLock().tryLock(10, TimeUnit.MILLISECONDS)) {
                    int c = terminal.reader().read(10);
                    if (c == -1) {
                        break;
                    }
                    if (c == '+' || c == '-' || c == CTRL_L || c == CTRL_M) {
                        queue.add(new Event(EventType.INPUT, null, Character.toString((char) c)));
                    }
                    readInput.readLock().unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
                    case BUILD_STATUS: {
                        this.buildStatus = entry.message;
                        break;
                    }
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
                            display.update(Collections.emptyList(), 0);
                            break;
                        case CTRL_M:
                            displayDone = !displayDone;
                            displayDone();
                            break;
                        }
                        break;
                    case DISPLAY:
                        display.update(Collections.emptyList(), 0);
                        terminal.writer().printf("[%s] %s%n", entry.projectId, entry.message);
                        break;
                    case PROMPT:
                    case PROMPT_PASSWORD: {
                        readInput.writeLock().lock();
                        try {
                            display.update(Collections.emptyList(), 0);
                            terminal.writer().printf("[%s] %s", entry.projectId, entry.message);
                            terminal.flush();
                            StringBuilder sb = new StringBuilder();
                            while (true) {
                                int c = terminal.reader().read();
                                if (c < 0) {
                                    break;
                                } else if (c == '\n' || c == '\r') {
                                    entry.response.put(sb.toString());
                                    terminal.writer().println();
                                    break;
                                } else if (c == 127) {
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
                        } finally {
                            readInput.writeLock().unlock();
                        }
                    }
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
        int dispLines = rows - 1; // for the build status line
        dispLines--; // there's a bug which sometimes make the cursor goes one line below, so keep one more line empty at the end
        final int projectsCount = projects.size();

        addStatusLine(lines, dispLines, projectsCount);

        if (projectsCount <= dispLines) {
            int remLogLines = dispLines - projectsCount;
            for (Project prj : projects.values()) {
                addProjectLine(lines, prj);
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
            int skipProjects = projectsCount - dispLines;
            for (Project prj : projects.values()) {
                if (skipProjects == 0) {
                    addProjectLine(lines, prj);
                } else {
                    skipProjects--;
                }
            }
        }
        List<AttributedString> trimmed = lines.stream()
                .map(s -> s.columnSubSequence(0, cols))
                .collect(Collectors.toList());
        display.update(trimmed, -1);
    }

    private void addStatusLine(final List<AttributedString> lines, int dispLines, final int projectsCount) {
        AttributedStringBuilder asb = new AttributedStringBuilder();
        StringBuilder statusLine = new StringBuilder(64);
        if (name == null) {
            statusLine.append(buildStatus != null ? buildStatus : "Looking up daemon...");
        } else {
            asb.append("Building ");
            asb.style(AttributedStyle.BOLD);
            asb.append(name);
            asb.style(AttributedStyle.DEFAULT);
            if (projectsCount <= dispLines) {
                statusLine.append("  threads used/max: ")
                        .append(projectsCount).append('/').append(maxThreads);
            } else {
                statusLine.append("  threads used/hidden/max: ")
                        .append(projectsCount).append('/').append(projectsCount - dispLines).append('/').append(maxThreads);
            }

            if (totalProjects > 0) {
                statusLine.append("  progress: ").append(doneProjects).append('/').append(totalProjects).append(' ')
                        .append(doneProjects * 100 / totalProjects).append('%');
            }
        }

        statusLine.append("  time: ");
        long sec = (System.currentTimeMillis() - this.start) / 1000;
        if (sec > 60) {
            statusLine.append(sec / 60).append('m').append(String.valueOf(sec % 60)).append('s');
        } else {
            statusLine.append(sec).append('s');
        }

        asb.append(statusLine.toString());
        lines.add(asb.toAttributedString());
    }

    private void addProjectLine(final List<AttributedString> lines, Project prj) {
        String str = prj.status != null ? prj.status : ":" + prj.id + ":<unknown>";
        if (str.length() >= 1 && str.charAt(0) == ':') {
            int ce = str.indexOf(':', 1);
            final AttributedStringBuilder asb = new AttributedStringBuilder();
            asb.append(":");
            asb.style(AttributedStyle.BOLD);
            if (ce > 0) {
                asb.append(str, 1, ce);
                asb.style(AttributedStyle.DEFAULT);
                asb.append(str, ce, str.length());
            } else {
                asb.append(str, 1, str.length());
            }
            lines.add(asb.toAttributedString());
        } else {
            lines.add(AttributedString.fromAnsi(str));
        }
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
