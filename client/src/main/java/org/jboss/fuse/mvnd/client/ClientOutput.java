package org.jboss.fuse.mvnd.client;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

import org.jboss.fuse.mvnd.client.Message.BuildException;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sink for various kinds of events sent by the daemon.
 */
public interface ClientOutput extends AutoCloseable {

    public void projectStateChanged(String projectId, String display);

    public void projectFinished(String projectId);

    public void log(String message);

    public void error(BuildException m);

    public void debug(String string);

    /**
     * A terminal {@link ClientOutput} based on JLine.
     */
    static class TerminalOutput implements ClientOutput {
        private static final Logger LOGGER = LoggerFactory.getLogger(TerminalOutput.class);
        private final TerminalUpdater updater;
        private final BlockingQueue<Map.Entry<String, String>> queue;
        public TerminalOutput(Path logFile) throws IOException {
            this.queue = new LinkedBlockingDeque<>();
            this.updater = new TerminalUpdater(queue, logFile);
        }

        public void projectStateChanged(String projectId, String task) {
            try {
                queue.put(new AbstractMap.SimpleImmutableEntry<>(projectId, task));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public void projectFinished(String projectId) {
            try {
                queue.put(new AbstractMap.SimpleImmutableEntry<>(projectId, null));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void log(String message) {
            try {
                queue.put(new AbstractMap.SimpleImmutableEntry<>(TerminalUpdater.LOG, message));
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
                queue.put(new AbstractMap.SimpleImmutableEntry<>(TerminalUpdater.ERROR, msg));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void debug(String msg) {
            LOGGER.debug(msg);
        }

        static class TerminalUpdater implements AutoCloseable {
            private static final String LOG = "<log>";
            private static final String ERROR = "<error>";
            private static final String END_OF_STREAM = "<eos>";
            private final BlockingQueue<Map.Entry<String, String>> queue;
            private final Terminal terminal;
            private final Display display;
            private final LinkedHashMap<String, String> projects = new LinkedHashMap<>();
            private final Log log;
            private final Thread worker;
            private volatile Exception exception;

            public TerminalUpdater(BlockingQueue<Entry<String, String>> queue, Path logFile) throws IOException {
                super();
                this.terminal = TerminalBuilder.terminal();
                this.display = new Display(terminal, false);
                this.log = logFile == null ? new ClientOutput.Log.MessageCollector(terminal)
                        : new ClientOutput.Log.FileLog(logFile);
                this.queue = queue;
                final Thread w = new Thread(this::run);
                w.start();
                this.worker = w;
            }

            void run() {
                final List<Entry<String, String>> entries = new ArrayList<>();

                while (true) {
                    try {
                        entries.add(queue.take());
                        queue.drainTo(entries);
                        for (Entry<String, String> entry : entries) {
                            final String key = entry.getKey();
                            final String value = entry.getValue();
                            if (key == END_OF_STREAM) {
                                display.update(Collections.emptyList(), 0);
                                LOGGER.debug("Done receiving, printing log");
                                log.close();
                                LOGGER.debug("Done !");
                                terminal.flush();
                                return;
                            } else if (key == LOG) {
                                log.accept(value);
                            } else if (key == ERROR) {
                                display.update(Collections.emptyList(), 0);
                                final AttributedStyle s = new AttributedStyle().bold().foreground(AttributedStyle.RED);
                                terminal.writer().println(new AttributedString(value, s).toAnsi());
                                terminal.flush();
                                return;
                            } else if (value == null) {
                                projects.remove(key);
                            } else {
                                projects.put(key, value);
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

            @Override
            public void close() throws Exception {
                queue.put(new AbstractMap.SimpleImmutableEntry<>(END_OF_STREAM, null));
                worker.join();
                if (exception != null) {
                    throw exception;
                }
            }

            private void update() {
                // no need to refresh the display at every single step
                final Size size = terminal.getSize();
                display.resize(size.getRows(), size.getColumns());
                final int displayableProjectCount = size.getRows() - 1;
                final int skipRows = projects.size() > displayableProjectCount ? projects.size() - displayableProjectCount : 0;
                final List<AttributedString> lines = new ArrayList<>(projects.size() - skipRows);
                final int lineMaxLength = size.getColumns();
                int i = 0;
                for (String line : projects.values()) {
                    if (i < skipRows) {
                        i++;
                    } else {
                        lines.add(shortenIfNeeded(AttributedString.fromAnsi(line), lineMaxLength));
                    }
                }
                lines.add(0, new AttributedString("Building..." + (skipRows > 0 ? " (" + skipRows + " more)" : "")));
                display.update(lines, -1);
            }

            static AttributedString shortenIfNeeded(AttributedString s, int length) {
                if (s == null) {
                    return null;
                }
                if (s.length() > length) {
                    return s.columnSubSequence(0, length - 1);
                }
                return s;
            }

        }

    }

    /**
     * A closeable string message consumer.
     */
    interface Log extends Consumer<String>, AutoCloseable {

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

            public MessageCollector(Terminal terminal) {
                super();
                this.terminal = terminal;
            }

            @Override
            public void accept(String message) {
                messages.add(message);
            }

            @Override
            public void close() {
                messages.forEach(terminal.writer()::println);
                terminal.flush();
            }

        }
    }

}
