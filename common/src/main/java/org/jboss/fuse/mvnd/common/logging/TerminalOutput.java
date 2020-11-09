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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.jboss.fuse.mvnd.common.Message;
import org.jboss.fuse.mvnd.common.Message.BuildEvent;
import org.jboss.fuse.mvnd.common.Message.BuildException;
import org.jboss.fuse.mvnd.common.Message.BuildMessage;
import org.jboss.fuse.mvnd.common.Message.BuildStarted;
import org.jboss.fuse.mvnd.common.Message.StringMessage;
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

    private final Terminal terminal;
    private final Terminal.SignalHandler previousIntHandler;
    private final Display display;
    private final LinkedHashMap<String, Project> projects = new LinkedHashMap<>();
    private final ClientLog log;
    private final Thread reader;
    private volatile Exception exception;
    private volatile boolean closing;
    private final CountDownLatch closed = new CountDownLatch(1);
    private final long start;
    private final ReadWriteLock readInput = new ReentrantReadWriteLock();

    /** A sink for sending messages back to the daemon */
    private volatile Consumer<Message> daemonDispatch;
    private volatile String name;
    private volatile int totalProjects;
    private volatile int maxThreads;

    private int linesPerProject = 0; // read/written only by the displayLoop
    private int doneProjects = 0; // read/written only by the displayLoop
    private String buildStatus; // read/written only by the displayLoop
    private boolean displayDone = false; // read/written only by the displayLoop

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
        this.terminal = TerminalBuilder.terminal();
        terminal.enterRawMode();
        Thread mainThread = Thread.currentThread();
        daemonDispatch = m -> {
            if (m == Message.CANCEL_BUILD_SINGLETON) {
                mainThread.interrupt();
            }
        };
        this.previousIntHandler = terminal.handle(Terminal.Signal.INT,
                sig -> daemonDispatch.accept(Message.CANCEL_BUILD_SINGLETON));
        this.display = new Display(terminal, false);
        this.log = logFile == null ? new MessageCollector() : new FileLog(logFile);
        final Thread r = new Thread(this::readInputLoop);
        r.start();
        this.reader = r;
    }

    @Override
    public void setDeamonDispatch(Consumer<Message> daemonDispatch) {
        this.daemonDispatch = daemonDispatch;
    }

    @Override
    public void accept(Message entry) {
        assert "main".equals(Thread.currentThread().getName());
        if (doAccept(entry)) {
            update();
        }
    }

    @Override
    public void accept(List<Message> entries) {
        assert "main".equals(Thread.currentThread().getName());
        for (Message entry : entries) {
            if (!doAccept(entry)) {
                return;
            }
        }
        update();
    }

    private boolean doAccept(Message entry) {
        switch (entry.getType()) {
        case Message.BUILD_STARTED: {
            BuildStarted bs = (BuildStarted) entry;
            this.name = bs.getProjectId();
            this.totalProjects = bs.getProjectCount();
            this.maxThreads = bs.getMaxThreads();
            break;
        }
        case Message.CANCEL_BUILD: {
            projects.values().stream().flatMap(p -> p.log.stream()).forEach(log);
            clearDisplay();
            try {
                log.close();
            } catch (IOException e1) {
                throw new RuntimeException(e1);
            }
            final AttributedStyle s = new AttributedStyle().bold().foreground(AttributedStyle.RED);
            new AttributedString("The build was canceled", s).println(terminal);
            terminal.flush();
            return false;
        }
        case Message.BUILD_EXCEPTION: {
            final BuildException e = (BuildException) entry;
            final String msg;
            if ("org.apache.commons.cli.UnrecognizedOptionException".equals(e.getClassName())) {
                msg = "Unable to parse command line options: " + e.getMessage();
            } else {
                msg = e.getClassName() + ": " + e.getMessage();
            }
            projects.values().stream().flatMap(p -> p.log.stream()).forEach(log);
            clearDisplay();
            try {
                log.close();
            } catch (IOException e1) {
                throw new RuntimeException(e1);
            }
            final AttributedStyle s = new AttributedStyle().bold().foreground(AttributedStyle.RED);
            new AttributedString(msg, s).println(terminal);
            terminal.flush();
            return false;
        }
        case Message.PROJECT_STARTED:
        case Message.MOJO_STARTED: {
            BuildEvent be = (BuildEvent) entry;
            Project prj = projects.computeIfAbsent(be.getProjectId(), Project::new);
            prj.status = be.getDisplay();
            break;
        }
        case Message.PROJECT_STOPPED: {
            BuildEvent be = (BuildEvent) entry;
            Project prj = projects.remove(be.getProjectId());
            if (prj != null) {
                prj.log.forEach(log);
            }
            doneProjects++;
            displayDone();
            break;
        }
        case Message.BUILD_STATUS: {
            this.buildStatus = ((StringMessage) entry).getPayload();
            break;
        }
        case Message.BUILD_STOPPED: {
            projects.values().stream().flatMap(p -> p.log.stream()).forEach(log);
            clearDisplay();
            try {
                log.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                terminal.flush();
            }
            return false;
        }
        case Message.KEEP_ALIVE: {
            break;
        }
        case Message.DISPLAY: {
            Message.Display d = (Message.Display) entry;
            display.update(Collections.emptyList(), 0);
            if (d.getProjectId() != null) {
                terminal.writer().printf("[%s] %s%n", d.getProjectId(), d.getMessage());
            } else {
                terminal.writer().printf("%s%n", d.getMessage());
            }
            break;
        }
        case Message.PROMPT: {
            Message.Prompt prompt = (Message.Prompt) entry;
            readInput.writeLock().lock();
            try {
                display.update(Collections.emptyList(), 0);
                terminal.writer().printf("[%s] %s", prompt.getProjectId(), prompt.getMessage());
                terminal.flush();
                StringBuilder sb = new StringBuilder();
                while (true) {
                    int c = terminal.reader().read();
                    if (c < 0) {
                        break;
                    } else if (c == '\n' || c == '\r') {
                        terminal.writer().println();
                        daemonDispatch.accept(prompt.response(sb.toString()));
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
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                readInput.writeLock().unlock();
            }
            break;
        }
        case Message.BUILD_MESSAGE: {
            BuildMessage bm = (BuildMessage) entry;
            if (bm.getProjectId() != null) {
                Project prj = projects.computeIfAbsent(bm.getProjectId(), Project::new);
                prj.log.add(bm.getMessage());
            } else {
                log.accept(bm.getMessage());
            }
            break;
        }
        case Message.KEYBOARD_INPUT: {
            char keyStroke = ((StringMessage) entry).getPayload().charAt(0);
            switch (keyStroke) {
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
        }
        default:
            throw new IllegalStateException("Unexpected message " + entry);
        }

        return true;
    }

    @Override
    public void describeTerminal() {
        StringBuilder sb = new StringBuilder();
        sb.append("Terminal: ").append(terminal != null ? terminal.getClass().getName() : null);
        if (terminal instanceof AbstractPosixTerminal) {
            sb.append(" with pty ").append(((AbstractPosixTerminal) terminal).getPty().getClass().getName());
        }
        this.accept(Message.log(sb.toString()));
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
                        accept(Message.keyboardInput((char) c));
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

    private void clearDisplay() {
        display.update(Collections.emptyList(), 0);
    }

    private void displayDone() {
        if (displayDone) {
            try {
                log.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void close() throws Exception {
        closing = true;
        reader.interrupt();
        log.close();
        reader.join();
        terminal.handle(Terminal.Signal.INT, previousIntHandler);
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
        if (name != null || buildStatus != null) {
            AttributedStringBuilder asb = new AttributedStringBuilder();
            StringBuilder statusLine = new StringBuilder(64);
            if (name != null) {
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
            } else if (buildStatus != null) {
                statusLine.append(buildStatus);
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
