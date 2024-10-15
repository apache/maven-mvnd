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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.HelpFormatter;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.OptionType;

/**
 * Combines the help message from the stock Maven with {@code mvnd}'s help message.
 */
public class MvndHelpFormatter {
    private static final Pattern HTML_TAGS_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern COLUMNS_DETECTOR_PATTERN = Pattern.compile("^[ ]+[^s]");
    private static final Pattern WS_PATTERN = Pattern.compile("\\s+");

    static String toPlainText(String javadocText) {
        return HTML_TAGS_PATTERN.matcher(javadocText).replaceAll("");
    }

    /**
     * Returns Maven option descriptions combined with mvnd options descriptions
     *
     * @param  cliManager The cli manager
     * @return            the string containing the help message
     */
    public static String displayHelp(CommonsCliDaemonMavenOptions cliManager) {
        int terminalWidth = getTerminalWidth();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(baos, false, StandardCharsets.UTF_8)) {
            out.println();
            PrintWriter pw = new PrintWriter(out);
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(
                    pw,
                    terminalWidth,
                    "mvnd [options] [<goal(s)>] [<phase(s)>]",
                    "\nOptions:",
                    cliManager.getOptions(),
                    1,
                    3,
                    "\n",
                    false);
            pw.flush();
        }
        final String mvnHelp = baos.toString(StandardCharsets.UTF_8);
        final Matcher m = COLUMNS_DETECTOR_PATTERN.matcher(mvnHelp);
        final String indent = m.find() ? m.group() : "                                        ";

        final String lineSeparator = System.lineSeparator();
        final StringBuilder help =
                new StringBuilder(mvnHelp).append(lineSeparator).append("mvnd specific options:");

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

        return help.toString();
    }

    private static int getTerminalWidth() {
        int terminalWidth;
        try {
            terminalWidth = Environment.MVND_TERMINAL_WIDTH.asInt();
        } catch (Exception e) {
            terminalWidth = 80;
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
                if (stringBuilder.length() + token.length() + (lastWs != null ? lastWs.length() : 0) < nextLineEnd) {
                    if (lastWs != null) {
                        stringBuilder.append(lastWs);
                    }
                    stringBuilder.append(token);

                } else {
                    nextLineEnd = stringBuilder.length() + lineLength;
                    stringBuilder.append(System.lineSeparator()).append(indent).append(token);
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
