package org.jboss.fuse.mvnd.client;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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

        private final Terminal terminal;
        private final Display display;
        private final LinkedHashMap<String, String> projects = new LinkedHashMap<>();
        private long lastUpdate = 0;
        private final Log log;

        public TerminalOutput(Path logFile) throws IOException {
            this.terminal = TerminalBuilder.terminal();
            this.display = new Display(terminal, false);
            this.log = logFile == null ? new ClientOutput.Log.MessageCollector(terminal)
                    : new ClientOutput.Log.FileLog(logFile);
        }

        public void projectStateChanged(String projectId, String task) {
            projects.put(projectId, task);
            update();
        }

        private void update() {
            // no need to refresh the display at every single step
            long curTime = System.currentTimeMillis();
            if (curTime - lastUpdate >= 10) {
                Size size = terminal.getSize();
                display.resize(size.getRows(), size.getColumns());
                List<AttributedString> lines = new ArrayList<>();
                projects.values().stream()
                        .map(AttributedString::fromAnsi)
                        .map(s -> s.columnSubSequence(0, size.getColumns() - 1))
                        .forEachOrdered(lines::add);
                // Make sure we don't try to display more lines than the terminal height
                int rem = 0;
                while (lines.size() >= terminal.getHeight()) {
                    lines.remove(0);
                    rem++;
                }
                lines.add(0, new AttributedString("Building..." + (rem > 0 ? " (" + rem + " more)" : "")));
                display.update(lines, -1);
                lastUpdate = curTime;
            }

        }

        public void projectFinished(String projectId) {
            projects.remove(projectId);
            update();
        }

        @Override
        public void log(String message) {
            log.accept(message);
        }

        @Override
        public void close() throws Exception {
            display.update(Collections.emptyList(), 0);
            LOGGER.debug("Done receiving, printing log");
            log.close();
            LOGGER.debug("Done !");
            terminal.flush();
        }

        @Override
        public void error(BuildException error) {
            display.update(Collections.emptyList(), 0);
            final AttributedStyle s = new AttributedStyle().bold().foreground(AttributedStyle.RED);
            final String msg;
            if ("org.apache.commons.cli.UnrecognizedOptionException".equals(error.getClassName())) {
                msg = "Unable to parse command line options: " + error.getMessage();
            } else {
                msg = error.getClassName() + ": " + error.getMessage();
            }
            terminal.writer().println(new AttributedString(msg, s).toAnsi());
            terminal.flush();
        }

        @Override
        public void debug(String msg) {
            LOGGER.debug(msg);
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
