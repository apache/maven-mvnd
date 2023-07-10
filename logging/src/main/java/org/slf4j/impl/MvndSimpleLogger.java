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
package org.slf4j.impl;

import java.util.Date;
import java.util.function.Consumer;

import org.apache.maven.shared.utils.logging.MessageBuilder;
import org.apache.maven.shared.utils.logging.MessageUtils;

import static org.apache.maven.shared.utils.logging.MessageUtils.level;

/**
 * Logger for Maven, that support colorization of levels and stacktraces. This class implements 2 methods introduced in
 * slf4j-simple provider local copy.
 *
 * @since 3.5.0
 */
public class MvndSimpleLogger extends MvndBaseLogger {

    static final String TID_PREFIX = "tid=";

    static long START_TIME = System.currentTimeMillis();

    static boolean INITIALIZED = false;
    static final SimpleLoggerConfiguration CONFIG_PARAMS = new SimpleLoggerConfiguration();

    static void lazyInit() {
        if (INITIALIZED) {
            return;
        }
        INITIALIZED = true;
        init();
    }

    // external software might be invoking this method directly. Do not rename
    // or change its semantics.
    static void init() {
        CONFIG_PARAMS.init();
    }

    static Consumer<String> LOG_SINK;

    public static void setLogSink(Consumer<String> logSink) {
        LOG_SINK = logSink;
    }

    /** The short name of this simple log instance */
    private transient String shortLogName = null;

    MvndSimpleLogger(String name) {
        super(name);
        configure(CONFIG_PARAMS.defaultLogLevel);
    }

    String recursivelyComputeLevelString() {
        String tempName = name;
        String levelString = null;
        int indexOfLastDot = tempName.length();
        while ((levelString == null) && (indexOfLastDot > -1)) {
            tempName = tempName.substring(0, indexOfLastDot);
            levelString = CONFIG_PARAMS.getStringProperty(SimpleLogger.LOG_KEY_PREFIX + tempName, null);
            indexOfLastDot = tempName.lastIndexOf(".");
        }
        return levelString;
    }

    @Override
    protected void doLog(int level, String message, Throwable t) {
        StringBuilder buf = new StringBuilder(32);

        // Append date-time if so configured
        if (CONFIG_PARAMS.showDateTime) {
            if (CONFIG_PARAMS.dateFormatter != null) {
                buf.append(getFormattedDate());
                buf.append(' ');
            } else {
                buf.append(System.currentTimeMillis() - START_TIME);
                buf.append(' ');
            }
        }

        // Append current thread name if so configured
        if (CONFIG_PARAMS.showThreadName) {
            buf.append('[');
            buf.append(Thread.currentThread().getName());
            buf.append("] ");
        }

        if (CONFIG_PARAMS.showThreadId) {
            buf.append(TID_PREFIX);
            buf.append(Thread.currentThread().getId());
            buf.append(' ');
        }

        if (CONFIG_PARAMS.levelInBrackets) buf.append('[');

        // Append a readable representation of the log level
        String levelStr = renderLevel(level);
        buf.append(levelStr);
        if (CONFIG_PARAMS.levelInBrackets) buf.append(']');
        buf.append(' ');

        // Append the name of the log instance if so configured
        if (CONFIG_PARAMS.showShortLogName) {
            if (shortLogName == null) shortLogName = computeShortName();
            buf.append(String.valueOf(shortLogName)).append(" - ");
        } else if (CONFIG_PARAMS.showLogName) {
            buf.append(String.valueOf(name)).append(" - ");
        }

        // Append the message
        buf.append(message);

        writeThrowable(t, buf);

        Consumer<String> sink = LOG_SINK;
        if (sink != null) {
            sink.accept(buf.toString());
        } else {
            CONFIG_PARAMS.outputChoice.getTargetPrintStream().println(buf.toString());
        }
    }

    protected String getFormattedDate() {
        Date now = new Date();
        return CONFIG_PARAMS.dateFormatter.format(now);
    }

    private String computeShortName() {
        return name.substring(name.lastIndexOf(".") + 1);
    }

    protected String renderLevel(int level) {
        switch (level) {
            case LOG_LEVEL_TRACE:
                return level().debug("TRACE").toString();
            case LOG_LEVEL_DEBUG:
                return level().debug("DEBUG").toString();
            case LOG_LEVEL_INFO:
                return level().info("INFO").toString();
            case LOG_LEVEL_WARN:
                return level().warning("WARNING").toString();
            case LOG_LEVEL_ERROR:
            default:
                return level().error("ERROR").toString();
        }
    }

    protected void writeThrowable(Throwable t, StringBuilder sb) {
        if (t == null) {
            return;
        }
        MessageBuilder builder = MessageUtils.buffer(sb);
        builder.failure(t.getClass().getName());
        if (t.getMessage() != null) {
            builder.a(": ");
            builder.failure(t.getMessage());
        }
        builder.newline();

        printStackTrace(t, builder, "");
    }

    private void printStackTrace(Throwable t, MessageBuilder builder, String prefix) {
        for (StackTraceElement e : t.getStackTrace()) {
            builder.a(prefix);
            builder.a("    ");
            builder.strong("at");
            builder.a(" " + e.getClassName() + "." + e.getMethodName());
            builder.a(" (").strong(getLocation(e)).a(")");
            builder.newline();
        }
        for (Throwable se : t.getSuppressed()) {
            writeThrowable(se, builder, "Suppressed", prefix + "    ");
        }
        Throwable cause = t.getCause();
        if (cause != null) {
            writeThrowable(cause, builder, "Caused by", prefix);
        }
    }

    private void writeThrowable(Throwable t, MessageBuilder builder, String caption, String prefix) {
        builder.a(prefix).strong(caption).a(": ").a(t.getClass().getName());
        if (t.getMessage() != null) {
            builder.a(": ");
            builder.failure(t.getMessage());
        }
        builder.newline();

        printStackTrace(t, builder, prefix);
    }

    protected String getLocation(final StackTraceElement e) {
        assert e != null;

        if (e.isNativeMethod()) {
            return "Native Method";
        } else if (e.getFileName() == null) {
            return "Unknown Source";
        } else if (e.getLineNumber() >= 0) {
            return String.format("%s:%s", e.getFileName(), e.getLineNumber());
        } else {
            return e.getFileName();
        }
    }

    public void configure(int defaultLogLevel) {
        String levelString = recursivelyComputeLevelString();
        if (levelString != null) {
            this.currentLogLevel = SimpleLoggerConfiguration.stringToLevel(levelString);
        } else {
            this.currentLogLevel = defaultLogLevel;
        }
    }

    public void setLogLevel(int logLevel) {
        this.currentLogLevel = logLevel;
    }
}
