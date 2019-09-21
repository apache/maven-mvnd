/*
 * Copyright (c) 2015-2016 salesforce.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.fuse.mvnd.logging.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replacement System out/err implementation that funnels most of the messages to slf4j logger.
 * <p>
 * The basic idea is to use current thread stack trace to determine package name of the code that
 * writes to System out/err and. If package belongs to a know logging framework (simplelogger,
 * logback or log4j), the call is passed to the original PrintStream. All other messages are logged
 * to an slf4j logger.
 * <p>
 * The implementation attempts to correlate multiple related calls to PrintStream. For example, if
 * the caller uses multiple {@code #print} calls to produce a complex console message, each message
 * line will be treated as single logging event.
 */
public class SLF4JPrintStream extends PrintStream {

    public static final String LOGGER_SYSTOUT = "SYSOUT";

    private static final ThreadLocal<Boolean> privileged = new ThreadLocal<>();

    private final ThreadLocal<LineSplitter> splitters = ThreadLocal.withInitial(LineSplitter::new);

    private final PrintStream stream;

    private final boolean error;

    public SLF4JPrintStream(PrintStream stream, boolean error) {
        super(new ByteArrayOutputStream());
        this.stream = stream;
        this.error = error;
    }

    private static boolean isPrivileged() {
        return privileged.get() != null;
    }

    public static void enterPrivileged() {
        privileged.set(Boolean.TRUE);
    }

    public static void leavePrivileged() {
        privileged.remove();
    }

    private LineSplitter splitter() {
        return splitters.get();
    }

    private void logln(String caller, String message) {
        message = splitter().flush() + message;
        Logger logger = getLogger(caller);
        if (error) {
            logger.warn(message);
        } else {
            logger.info(message);
        }
    }

    protected Logger getLogger(String caller) {
        return LoggerFactory.getLogger(LOGGER_SYSTOUT + "." + caller);
    }

    private void log(String caller, String message) {
        log(caller, splitter().split(message));
    }

    private void log(String caller, byte[] buf, int off, int len) {
        log(caller, splitter().split(buf, off, len));
    }

    protected void log(String caller, Collection<String> strings) {
        if (strings.isEmpty()) {
            return;
        }
        Logger logger = getLogger(caller);
        for (String buffered : strings) {
            if (error) {
                logger.warn(buffered);
            } else {
                logger.info(buffered);
            }
        }
    }

    private boolean isPrivileged(String caller) {
        if (caller == null || caller.trim().isEmpty()) {
            return true; // don't know who the caller is, assume it's privileged
        }
        return caller.startsWith("org.slf4j") // slf4j and simple logger
                || caller.startsWith("ch.qos") // logback
                || caller.startsWith("org.apache.logging.log4j") // log4j
                ;
    }

    /**
     * Tries to guess classname of the code that called into System out/err by walking current thread
     * stack trace. Skips frames that correspond to this class and any java.* and javax.* classes.
     * Returns empty string if caller class name cannot be determined.
     */
    private String getCaller() {
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        for (int i = 2; i < frames.length; i++) {
            String className = frames[i].getClassName();
            if (!className.equals(getClass().getName()) //
                    && !className.contains("java.") //
                    && !className.contains("javax.")) {
                return className;
            }
        }
        return "";
    }

    @Override
    public void println(String value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.println(value);
        } else {
            logln(caller, value);
        }
    }

    @Override
    public void println(Object value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.println(value);
        } else {
            logln(caller, String.valueOf(value));
        }
    }

    @Override
    public void println() {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.println();
        } else {
            logln(caller, "");
        }
    }

    @Override
    public void println(boolean value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.println(value);
        } else {
            logln(caller, String.valueOf(value));
        }
    }

    @Override
    public void println(char value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.println(value);
        } else {
            logln(caller, String.valueOf(value));
        }
    }

    @Override
    public void println(char[] value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.println(value);
        } else {
            logln(caller, String.valueOf(value));
        }
    }

    @Override
    public void println(double value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.println(value);
        } else {
            logln(caller, String.valueOf(value));
        }
    }

    @Override
    public void println(float value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.println(value);
        } else {
            logln(caller, String.valueOf(value));
        }
    }

    @Override
    public void println(int value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.println(value);
        } else {
            logln(caller, String.valueOf(value));
        }
    }

    @Override
    public void println(long value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.println(value);
        } else {
            logln(caller, String.valueOf(value));
        }
    }

    @Override
    public PrintStream append(char value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.append(value);
        } else {
            log(caller, String.valueOf(value));
        }
        return this;
    }

    @Override
    public PrintStream append(CharSequence string, int start, int end) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.append(string, start, end);
        } else {
            log(caller, string.subSequence(start, end).toString());
        }
        return this;
    }

    @Override
    public PrintStream append(CharSequence value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.append(value);
        } else {
            log(caller, value.toString());
        }
        return this;
    }

    @Override
    public boolean checkError() {
        return stream.checkError();
    }

    @Override
    protected void setError() {
        stream.printf("%s#setError() is not supported\n", getClass().getName());
    }

    @Override
    public void close() {
        stream.close();
    }

    @Override
    public void flush() {
        stream.flush();
    }

    @Override
    public PrintStream format(Locale locale, String format, Object... args) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.format(locale, format, args);
        } else {
            log(caller, String.format(locale, format, args));
        }
        return this;
    }

    @Override
    public PrintStream format(String format, Object... args) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.format(format, args);
        } else {
            log(caller, String.format(format, args));
        }
        return this;
    }

    @Override
    public void print(boolean value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.print(value);
        } else {
            log(caller, String.valueOf(value));
        }
    }

    @Override
    public void print(char value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.print(value);
        } else {
            log(caller, String.valueOf(value));
        }
    }

    @Override
    public void print(char[] value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.print(value);
        } else {
            log(caller, String.valueOf(value));
        }
    }

    @Override
    public void print(double value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.print(value);
        } else {
            log(caller, String.valueOf(value));
        }
    }

    @Override
    public void print(float value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.print(value);
        } else {
            log(caller, String.valueOf(value));
        }
    }

    @Override
    public void print(int value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.print(value);
        } else {
            log(caller, String.valueOf(value));
        }
    }

    @Override
    public void print(long value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.print(value);
        } else {
            log(caller, String.valueOf(value));
        }
    }

    @Override
    public void print(Object value) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.print(value);
        } else {
            log(caller, String.valueOf(value));
        }
    }

    @Override
    public void print(String string) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.print(string);
        } else {
            log(caller, string);
        }
    }

    @Override
    public PrintStream printf(Locale locale, String format, Object... args) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.printf(locale, format, args);
        } else {
            log(caller, String.format(locale, format, args));
        }
        return this;
    }

    @Override
    public PrintStream printf(String format, Object... args) {
        String caller = getCaller();
        if (isPrivileged(caller)) {
            stream.printf(format, args);
        } else {
            log(caller, String.format(format, args));
        }
        return this;
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        if (isPrivileged()) {
            stream.write(buf, off, len);
        } else {
            String caller = getCaller();
            if (isPrivileged(caller)) {
                stream.write(buf, off, len);
            } else {
                log(caller, buf, off, len);
            }
        }
    }

    @Override
    public void write(int b) {
        if (isPrivileged()) {
            stream.write(b);
        } else {
            String caller = getCaller();
            if (isPrivileged(caller)) {
                stream.write(b);
            } else {
                log(caller, new byte[]{(byte) b}, 0, 1);
            }
        }
    }

    @Override
    public void write(byte[] buf) throws IOException {
        if (isPrivileged()) {
            stream.write(buf);
        } else {
            String caller = getCaller();
            if (isPrivileged(caller)) {
                stream.write(buf);
            } else {
                log(caller, buf, 0, buf.length);
            }
        }
    }
}
