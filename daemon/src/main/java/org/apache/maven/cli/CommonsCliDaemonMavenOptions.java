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
package org.apache.maven.cli;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.maven.cling.invoker.mvn.CommonsCliMavenOptions;
import org.apache.maven.jline.MessageUtils;
import org.codehaus.plexus.interpolation.BasicInterpolator;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.OptionType;

import static org.apache.maven.cling.invoker.Utils.createInterpolator;

public class CommonsCliDaemonMavenOptions extends CommonsCliMavenOptions implements DaemonMavenOptions {
    public static CommonsCliDaemonMavenOptions parse(String source, String[] args) throws ParseException {
        CLIManager cliManager = new CLIManager();
        return new CommonsCliDaemonMavenOptions(source, cliManager, cliManager.parse(args));
    }

    protected CommonsCliDaemonMavenOptions(String source, CLIManager cliManager, CommandLine commandLine) {
        super(source, cliManager, commandLine);
    }

    public org.apache.commons.cli.Options getOptions() {
        return this.cliManager.getOptions();
    }

    private static CommonsCliDaemonMavenOptions interpolate(
            CommonsCliDaemonMavenOptions options, Collection<Map<String, String>> properties) {
        try {
            // now that we have properties, interpolate all arguments
            BasicInterpolator interpolator = createInterpolator(properties);
            CommandLine.Builder commandLineBuilder = new CommandLine.Builder();
            commandLineBuilder.setDeprecatedHandler(o -> {});
            for (Option option : options.commandLine.getOptions()) {
                if (!CLIManager.USER_PROPERTY.equals(option.getOpt())) {
                    List<String> values = option.getValuesList();
                    for (ListIterator<String> it = values.listIterator(); it.hasNext(); ) {
                        it.set(interpolator.interpolate(it.next()));
                    }
                }
                commandLineBuilder.addOption(option);
            }
            for (String arg : options.commandLine.getArgList()) {
                commandLineBuilder.addArg(interpolator.interpolate(arg));
            }
            return new CommonsCliDaemonMavenOptions(
                    options.source, (CLIManager) options.cliManager, commandLineBuilder.build());
        } catch (InterpolationException e) {
            throw new IllegalArgumentException("Could not interpolate CommonsCliOptions", e);
        }
    }

    @Override
    public DaemonMavenOptions interpolate(Collection<Map<String, String>> properties) {
        return interpolate(this, properties);
    }

    protected static class CLIManager extends CommonsCliMavenOptions.CLIManager {
        public static final String RAW_STREAMS = "ras";

        private static final Pattern HTML_TAGS_PATTERN = Pattern.compile("<[^>]*>");
        private static final Pattern COLUMNS_DETECTOR_PATTERN = Pattern.compile("^[ ]+[^s]");
        private static final Pattern WS_PATTERN = Pattern.compile("\\s+");

        static String toPlainText(String javadocText) {
            return HTML_TAGS_PATTERN.matcher(javadocText).replaceAll("");
        }

        @Override
        protected void prepareOptions(Options options) {
            super.prepareOptions(options);
            options.addOption(Option.builder(RAW_STREAMS)
                    .longOpt("raw-streams")
                    .desc("Use raw-streams for daemon communication")
                    .build());
        }

        @Override
        public void displayHelp(String command, Consumer<String> pw) {
            List<String> mvnHelp = new ArrayList<>();
            super.displayHelp(command, mvnHelp::add);
            final Matcher m = COLUMNS_DETECTOR_PATTERN.matcher(String.join("\n", mvnHelp));
            final String indent = m.find() ? m.group() : "                                        ";

            int terminalWidth = getTerminalWidth() <= 0 ? 74 : getTerminalWidth();
            final String lineSeparator = System.lineSeparator();
            final StringBuilder help = new StringBuilder().append(lineSeparator).append("mvnd specific options:");

            Environment.documentedEntries().forEach(entry -> {
                final Environment env = entry.getEntry();
                help.append(lineSeparator);
                int indentPos = help.length() + indent.length();
                int lineEnd = help.length() + terminalWidth;
                spaces(help, HelpFormatter.DEFAULT_LEFT_PAD);
                final String property = env.getProperty();
                if (property != null) {
                    help.append("-D").append(property);
                    if (env.getType() != OptionType.VOID) {
                        help.append("=<")
                                .append(env.getType().name().toLowerCase(Locale.ROOT))
                                .append('>');
                    }
                }

                final Set<String> opts = env.getOptions();
                if (!opts.isEmpty()) {
                    if (property != null) {
                        help.append(';');
                    }
                    boolean first = true;
                    for (String opt : opts) {
                        if (first) {
                            first = false;
                        } else {
                            help.append(',');
                        }
                        help.append(opt);
                    }
                    if (env.getType() != OptionType.VOID) {
                        help.append(" <")
                                .append(env.getType().name().toLowerCase(Locale.ROOT))
                                .append('>');
                    }
                }
                help.append(' ');

                spaces(help, indentPos - help.length());
                wrap(help, toPlainText(entry.getJavaDoc()), terminalWidth, lineEnd, indent);

                if (env.isDocumentedAsDiscriminating()) {
                    indentedLine(help, terminalWidth, "This is a discriminating start parameter.", indent);
                }
                if (env.getDefault() != null) {
                    indentedLine(help, terminalWidth, "Default: " + env.getDefault(), indent);
                }
                if (env.getEnvironmentVariable() != null) {
                    indentedLine(help, terminalWidth, "Env. variable: " + env.getEnvironmentVariable(), indent);
                }
            });

            help.append(lineSeparator).append(lineSeparator).append("mvnd value types:");

            OptionType.documentedEntries().forEach(entry -> {
                final OptionType type = entry.getEntry();
                help.append(lineSeparator);
                int indentPos = help.length() + indent.length();
                int lineEnd = help.length() + terminalWidth;
                spaces(help, HelpFormatter.DEFAULT_LEFT_PAD);
                help.append(type.name().toLowerCase(Locale.ROOT));
                spaces(help, indentPos - help.length());
                wrap(help, toPlainText(entry.getJavaDoc()), terminalWidth, lineEnd, indent);
            });
            Stream.concat(mvnHelp.stream(), Stream.of(help.toString().split(lineSeparator)))
                    .forEach(pw);
        }

        private static int getTerminalWidth() {
            int terminalWidth = MessageUtils.getTerminalWidth();
            if (terminalWidth <= 0) {
                terminalWidth = HelpFormatter.DEFAULT_WIDTH;
            }
            return terminalWidth;
        }

        private static void indentedLine(StringBuilder stringBuilder, int terminalWidth, String text, String indent) {
            final int lineEnd = stringBuilder.length() + terminalWidth;
            stringBuilder.append(System.lineSeparator()).append(indent);
            wrap(stringBuilder, text, terminalWidth, lineEnd, indent);
        }

        /**
         * Word-wrap the given {@code text} to the given {@link StringBuilder}
         *
         * @param stringBuilder the {@link StringBuilder} to append to
         * @param text          the text to wrap and append
         * @param lineLength    the preferred line length
         * @param nextLineEnd   the length of the {@code stringBuilder} at which the current line should end
         * @param indent        the indentation string
         */
        static void wrap(StringBuilder stringBuilder, String text, int lineLength, int nextLineEnd, String indent) {
            final StringTokenizer st = new StringTokenizer(text, " \t\n\r", true);
            String lastWs = null;
            while (st.hasMoreTokens()) {
                final String token = st.nextToken();
                if (WS_PATTERN.matcher(token).matches()) {
                    lastWs = token;
                } else {
                    if (stringBuilder.length() + token.length() + (lastWs != null ? lastWs.length() : 0)
                            < nextLineEnd) {
                        if (lastWs != null) {
                            stringBuilder.append(lastWs);
                        }
                        stringBuilder.append(token);

                    } else {
                        nextLineEnd = stringBuilder.length() + lineLength;
                        stringBuilder
                                .append(System.lineSeparator())
                                .append(indent)
                                .append(token);
                    }
                    lastWs = null;
                }
            }
        }

        /**
         * Append {@code count} spaces to the given {@code stringBuilder}
         *
         * @param  stringBuilder the {@link StringBuilder} to append to
         * @param  count         the number of spaces to append
         */
        static void spaces(StringBuilder stringBuilder, int count) {
            stringBuilder.append(" ".repeat(Math.max(0, count)));
        }
    }
}
