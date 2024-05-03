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
package org.mvndaemon.mvnd.logging.slf4j;

import java.util.function.Consumer;

import org.apache.maven.api.services.MessageBuilder;
import org.apache.maven.jline.MessageUtils;

import static org.apache.maven.jline.MessageUtils.builder;

/**
 * Logger for Maven, that support colorization of levels and stacktraces. This class implements 2 methods introduced in
 * slf4j-simple provider local copy.
 *
 * @since 3.5.0
 */
public class MvndSimpleLogger extends MvndBaseLogger {

    /*
    static final String TID_PREFIX = "tid=";

    static long START_TIME = System.currentTimeMillis();

    static boolean INITIALIZED = false;

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

     */

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

    @Override
    void write(StringBuilder buf, Throwable t) {
        writeThrowable(t, buf);
        Consumer<String> sink = LOG_SINK;
        if (sink != null) {
            sink.accept(buf.toString());
        } else {
            CONFIG_PARAMS.outputChoice.getTargetPrintStream().println(buf.toString());
        }
    }

    protected String renderLevel(int level) {
        switch (level) {
            case LOG_LEVEL_TRACE:
                return builder().trace("TRACE").build();
            case LOG_LEVEL_DEBUG:
                return builder().debug("DEBUG").build();
            case LOG_LEVEL_INFO:
                return builder().info("INFO").build();
            case LOG_LEVEL_WARN:
                return builder().warning("WARNING").build();
            case LOG_LEVEL_ERROR:
            default:
                return builder().error("ERROR").build();
        }
    }

    protected void writeThrowable(Throwable t, StringBuilder sb) {
        if (t == null) {
            return;
        }
        MessageBuilder builder = MessageUtils.builder();
        builder.failure(t.getClass().getName());
        if (t.getMessage() != null) {
            builder.a(": ");
            builder.failure(t.getMessage());
        }
        builder.newline();
        sb.append(builder);

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
