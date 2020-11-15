/*
 * Copyright 2019 the original author or authors.
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
package org.mvndaemon.mvnd.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UTFDataFormatException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class Message {
    public static final int BUILD_REQUEST = 0;
    public static final int BUILD_STARTED = 1;
    public static final int BUILD_FINISHED = 2;
    public static final int PROJECT_STARTED = 3;
    public static final int PROJECT_STOPPED = 4;
    public static final int MOJO_STARTED = 5;
    public static final int PROJECT_LOG_MESSAGE = 6;
    public static final int BUILD_LOG_MESSAGE = 7;
    public static final int BUILD_EXCEPTION = 8;
    public static final int KEEP_ALIVE = 9;
    public static final int STOP = 10;
    public static final int DISPLAY = 11;
    public static final int PROMPT = 12;
    public static final int PROMPT_RESPONSE = 13;
    public static final int BUILD_STATUS = 14;
    public static final int KEYBOARD_INPUT = 15;
    public static final int CANCEL_BUILD = 16;

    public static final BareMessage KEEP_ALIVE_SINGLETON = new BareMessage(KEEP_ALIVE);
    public static final BareMessage STOP_SINGLETON = new BareMessage(STOP);
    public static final BareMessage CANCEL_BUILD_SINGLETON = new BareMessage(CANCEL_BUILD);

    final int type;

    Message(int type) {
        this.type = type;
    }

    public static Message read(DataInputStream input) throws IOException {
        int type = input.read();
        if (type == -1) {
            return null;
        }
        switch (type) {
        case BUILD_REQUEST:
            return BuildRequest.read(input);
        case BUILD_STARTED:
            return BuildStarted.read(input);
        case BUILD_FINISHED:
            return BuildFinished.read(input);
        case PROJECT_STARTED:
        case PROJECT_STOPPED:
        case MOJO_STARTED:
        case PROJECT_LOG_MESSAGE:
        case DISPLAY:
            return ProjectEvent.read(type, input);
        case BUILD_EXCEPTION:
            return BuildException.read(input);
        case KEEP_ALIVE:
            return BareMessage.KEEP_ALIVE_SINGLETON;
        case STOP:
            return BareMessage.STOP_SINGLETON;
        case PROMPT:
            return Prompt.read(input);
        case PROMPT_RESPONSE:
            return PromptResponse.read(input);
        case BUILD_STATUS:
        case BUILD_LOG_MESSAGE:
            return StringMessage.read(type, input);
        case CANCEL_BUILD:
            return BareMessage.CANCEL_BUILD_SINGLETON;
        }
        throw new IllegalStateException("Unexpected message type: " + type);
    }

    final long timestamp = System.nanoTime();

    public long timestamp() {
        return timestamp;
    }

    public void write(DataOutputStream output) throws IOException {
        output.write(type);
    }

    static void writeStringList(DataOutputStream output, List<String> value) throws IOException {
        output.writeInt(value.size());
        for (String v : value) {
            writeUTF(output, v);
        }
    }

    static void writeStringMap(DataOutputStream output, Map<String, String> value) throws IOException {
        output.writeInt(value.size());
        for (Map.Entry<String, String> e : value.entrySet()) {
            writeUTF(output, e.getKey());
            writeUTF(output, e.getValue());
        }
    }

    static List<String> readStringList(DataInputStream input) throws IOException {
        ArrayList<String> l = new ArrayList<>();
        int nb = input.readInt();
        for (int i = 0; i < nb; i++) {
            l.add(readUTF(input));
        }
        return l;
    }

    static Map<String, String> readStringMap(DataInputStream input) throws IOException {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        int nb = input.readInt();
        for (int i = 0; i < nb; i++) {
            String k = readUTF(input);
            String v = readUTF(input);
            m.put(k, v);
        }
        return m;
    }

    private static final String INVALID_BYTE = "Invalid byte";
    private static final int UTF_BUFS_CHAR_CNT = 256;
    private static final int UTF_BUFS_BYTE_CNT = UTF_BUFS_CHAR_CNT * 3;
    private static final ThreadLocal<byte[]> BUF_TLS = ThreadLocal.withInitial(() -> new byte[UTF_BUFS_BYTE_CNT]);

    static String readUTF(DataInputStream input) throws IOException {
        byte[] byteBuf = BUF_TLS.get();
        int len = input.readInt();
        if (len == -1) {
            return null;
        }
        final char[] chars = new char[len];
        int i = 0, cnt = 0, charIdx = 0;
        while (charIdx < len) {
            if (i == cnt) {
                cnt = input.read(byteBuf, 0, Math.min(UTF_BUFS_BYTE_CNT, len - charIdx));
                if (cnt < 0) {
                    throw new EOFException();
                }
                i = 0;
            }
            final int a = byteBuf[i++] & 0xff;
            if (a < 0x80) {
                // low bit clear
                chars[charIdx++] = (char) a;
            } else if (a < 0xc0) {
                throw new UTFDataFormatException(INVALID_BYTE);
            } else if (a < 0xe0) {
                if (i == cnt) {
                    cnt = input.read(byteBuf, 0, Math.min(UTF_BUFS_BYTE_CNT, len - charIdx));
                    if (cnt < 0) {
                        throw new EOFException();
                    }
                    i = 0;
                }
                final int b = byteBuf[i++] & 0xff;
                if ((b & 0xc0) != 0x80) {
                    throw new UTFDataFormatException(INVALID_BYTE);
                }
                chars[charIdx++] = (char) ((a & 0x1f) << 6 | b & 0x3f);
            } else if (a < 0xf0) {
                if (i == cnt) {
                    cnt = input.read(byteBuf, 0, Math.min(UTF_BUFS_BYTE_CNT, len - charIdx));
                    if (cnt < 0) {
                        throw new EOFException();
                    }
                    i = 0;
                }
                final int b = byteBuf[i++] & 0xff;
                if ((b & 0xc0) != 0x80) {
                    throw new UTFDataFormatException(INVALID_BYTE);
                }
                if (i == cnt) {
                    cnt = input.read(byteBuf, 0, Math.min(UTF_BUFS_BYTE_CNT, len - charIdx));
                    if (cnt < 0) {
                        throw new EOFException();
                    }
                    i = 0;
                }
                final int c = byteBuf[i++] & 0xff;
                if ((c & 0xc0) != 0x80) {
                    throw new UTFDataFormatException(INVALID_BYTE);
                }
                chars[charIdx++] = (char) ((a & 0x0f) << 12 | (b & 0x3f) << 6 | c & 0x3f);
            } else {
                throw new UTFDataFormatException(INVALID_BYTE);
            }
        }
        return String.valueOf(chars);
    }

    static void writeUTF(DataOutputStream output, String s) throws IOException {
        byte[] byteBuf = BUF_TLS.get();
        if (s == null) {
            output.writeInt(-1);
            return;
        }
        final int length = s.length();
        output.writeInt(length);
        int strIdx = 0;
        int byteIdx = 0;
        while (strIdx < length) {
            final char c = s.charAt(strIdx++);
            if (c > 0 && c <= 0x7f) {
                byteBuf[byteIdx++] = (byte) c;
            } else if (c <= 0x07ff) {
                byteBuf[byteIdx++] = (byte) (0xc0 | 0x1f & c >> 6);
                byteBuf[byteIdx++] = (byte) (0x80 | 0x3f & c);
            } else {
                byteBuf[byteIdx++] = (byte) (0xe0 | 0x0f & c >> 12);
                byteBuf[byteIdx++] = (byte) (0x80 | 0x3f & c >> 6);
                byteBuf[byteIdx++] = (byte) (0x80 | 0x3f & c);
            }
            if (byteIdx > UTF_BUFS_BYTE_CNT - 4) {
                output.write(byteBuf, 0, byteIdx);
                byteIdx = 0;
            }
        }
        if (byteIdx > 0) {
            output.write(byteBuf, 0, byteIdx);
        }
    }

    public static class BuildRequest extends Message {
        final List<String> args;
        final String workingDir;
        final String projectDir;
        final Map<String, String> env;

        public static Message read(DataInputStream input) throws IOException {
            List<String> args = readStringList(input);
            String workingDir = readUTF(input);
            String projectDir = readUTF(input);
            Map<String, String> env = readStringMap(input);
            return new BuildRequest(args, workingDir, projectDir, env);
        }

        public BuildRequest(List<String> args, String workingDir, String projectDir, Map<String, String> env) {
            super(BUILD_REQUEST);
            this.args = args;
            this.workingDir = workingDir;
            this.projectDir = projectDir;
            this.env = env;
        }

        public List<String> getArgs() {
            return args;
        }

        public String getWorkingDir() {
            return workingDir;
        }

        public String getProjectDir() {
            return projectDir;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        @Override
        public String toString() {
            return "BuildRequest{" +
                    "args=" + args +
                    ", workingDir='" + workingDir + '\'' +
                    ", projectDir='" + projectDir + '\'' +
                    '}';
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            super.write(output);
            writeStringList(output, args);
            writeUTF(output, workingDir);
            writeUTF(output, projectDir);
            writeStringMap(output, env);
        }
    }

    public static class BuildFinished extends Message {
        final int exitCode;

        public static Message read(DataInputStream input) throws IOException {
            return new BuildFinished(input.readInt());
        }

        public BuildFinished(int exitCode) {
            super(BUILD_FINISHED);
            this.exitCode = exitCode;
        }

        @Override
        public String toString() {
            return "BuildRequest{exitCode=" + exitCode + '}';
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            super.write(output);
            output.writeInt(exitCode);
        }

        public int getExitCode() {
            return exitCode;
        }
    }

    public static class BuildException extends Message {
        final String message;
        final String className;
        final String stackTrace;

        public static Message read(DataInputStream input) throws IOException {
            String message = readUTF(input);
            String className = readUTF(input);
            String stackTrace = readUTF(input);
            return new BuildException(message, className, stackTrace);
        }

        public BuildException(Throwable t) {
            this(t.getMessage(), t.getClass().getName(), getStackTrace(t));
        }

        public static String getStackTrace(Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw, true));
            return sw.toString();
        }

        public BuildException(String message, String className, String stackTrace) {
            super(BUILD_EXCEPTION);
            this.message = message;
            this.className = className;
            this.stackTrace = stackTrace;
        }

        public String getMessage() {
            return message;
        }

        public String getClassName() {
            return className;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        @Override
        public String toString() {
            return "BuildException{" +
                    "message='" + message + '\'' +
                    ", className='" + className + '\'' +
                    ", stackTrace='" + stackTrace + '\'' +
                    '}';
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            super.write(output);
            writeUTF(output, message);
            writeUTF(output, className);
            writeUTF(output, stackTrace);
        }
    }

    public static class ProjectEvent extends Message {
        final String projectId;
        final String message;

        public static Message read(int type, DataInputStream input) throws IOException {
            String projectId = readUTF(input);
            String message = readUTF(input);
            return new ProjectEvent(type, projectId, message);
        }

        private ProjectEvent(int type, String projectId, String message) {
            super(type);
            this.projectId = Objects.requireNonNull(projectId, "projectId cannot be null");
            this.message = Objects.requireNonNull(message, "message cannot be null");
        }

        public String getProjectId() {
            return projectId;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return mnemonic() + "{" +
                    "projectId='" + projectId + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }

        private String mnemonic() {
            switch (type) {
            case PROJECT_STARTED:
                return "ProjectStarted";
            case PROJECT_STOPPED:
                return "ProjectStopped";
            case MOJO_STARTED:
                return "MojoStarted";
            case PROJECT_LOG_MESSAGE:
                return "ProjectLogMessage";
            default:
                throw new IllegalStateException("Unexpected type " + type);
            }
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            super.write(output);
            writeUTF(output, projectId);
            writeUTF(output, message);
        }
    }

    public static class BuildStarted extends Message {

        final String projectId;
        final int projectCount;
        final int maxThreads;

        public static BuildStarted read(DataInputStream input) throws IOException {
            final String projectId = readUTF(input);
            final int projectCount = input.readInt();
            final int maxThreads = input.readInt();
            return new BuildStarted(projectId, projectCount, maxThreads);
        }

        public BuildStarted(String projectId, int projectCount, int maxThreads) {
            super(BUILD_STARTED);
            this.projectId = projectId;
            this.projectCount = projectCount;
            this.maxThreads = maxThreads;
        }

        public String getProjectId() {
            return projectId;
        }

        public int getProjectCount() {
            return projectCount;
        }

        public int getMaxThreads() {
            return maxThreads;
        }

        @Override
        public String toString() {
            return "BuildStarted{" +
                    "projectId='" + projectId + "', projectCount=" + projectCount +
                    ", maxThreads='" + maxThreads + "'}";
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            super.write(output);
            writeUTF(output, projectId);
            output.writeInt(projectCount);
            output.writeInt(maxThreads);
        }

    }

    public static class BareMessage extends Message {

        private BareMessage(int type) {
            super(type);
        }

        @Override
        public String toString() {
            switch (type) {
            case KEEP_ALIVE:
                return "KeepAlive";
            case BUILD_FINISHED:
                return "BuildStopped";
            case STOP:
                return "Stop";
            case CANCEL_BUILD:
                return "BuildCanceled";
            default:
                throw new IllegalStateException("Unexpected type " + type);
            }
        }

    }

    public static class StringMessage extends Message {

        final String message;

        public static StringMessage read(int type, DataInputStream input) throws IOException {
            String message = readUTF(input);
            return new StringMessage(type, message);
        }

        private StringMessage(int type, String message) {
            super(type);
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            super.write(output);
            writeUTF(output, message);
        }

        @Override
        public String toString() {
            return mnemonic() + "{payload='" + message + "'}";
        }

        private String mnemonic() {
            switch (type) {
            case BUILD_STATUS:
                return "BuildStatus";
            case KEYBOARD_INPUT:
                return "KeyboardInput";
            case BUILD_LOG_MESSAGE:
                return "BuildLogMessage";
            case DISPLAY:
                return "Display";
            default:
                throw new IllegalStateException("Unexpected type " + type);
            }
        }

    }

    public static class Prompt extends Message {

        final String projectId;
        final String uid;
        final String message;
        final boolean password;

        public static Prompt read(DataInputStream input) throws IOException {
            String projectId = Message.readUTF(input);
            String uid = Message.readUTF(input);
            String message = Message.readUTF(input);
            boolean password = input.readBoolean();
            return new Prompt(projectId, uid, message, password);
        }

        public Prompt(String projectId, String uid, String message, boolean password) {
            super(PROMPT);
            this.projectId = projectId;
            this.uid = uid;
            this.message = message;
            this.password = password;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getUid() {
            return uid;
        }

        public String getMessage() {
            return message;
        }

        public boolean isPassword() {
            return password;
        }

        @Override
        public String toString() {
            return "Prompt{" +
                    "projectId='" + projectId + '\'' +
                    ", uid='" + uid + '\'' +
                    ", message='" + message + '\'' +
                    ", password=" + password +
                    '}';
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            super.write(output);
            writeUTF(output, projectId);
            writeUTF(output, uid);
            writeUTF(output, message);
            output.writeBoolean(password);
        }

        public PromptResponse response(String message) {
            return new PromptResponse(projectId, uid, message);
        }

    }

    public static class PromptResponse extends Message {

        final String projectId;
        final String uid;
        final String message;

        public static Message read(DataInputStream input) throws IOException {
            String projectId = Message.readUTF(input);
            String uid = Message.readUTF(input);
            String message = Message.readUTF(input);
            return new PromptResponse(projectId, uid, message);
        }

        private PromptResponse(String projectId, String uid, String message) {
            super(PROMPT_RESPONSE);
            this.projectId = projectId;
            this.uid = uid;
            this.message = message;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getUid() {
            return uid;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "PromptResponse{" +
                    "projectId='" + projectId + '\'' +
                    ", uid='" + uid + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            super.write(output);
            writeUTF(output, projectId);
            writeUTF(output, uid);
            writeUTF(output, message);
        }
    }

    public int getType() {
        return type;
    }

    public static StringMessage buildStatus(String payload) {
        return new StringMessage(BUILD_STATUS, payload);
    }

    public static StringMessage display(String message) {
        return new StringMessage(DISPLAY, message);
    }

    public static StringMessage log(String message) {
        return new StringMessage(BUILD_LOG_MESSAGE, message);
    }

    public static ProjectEvent log(String projectId, String message) {
        return new ProjectEvent(PROJECT_LOG_MESSAGE, projectId, message);
    }

    public static StringMessage keyboardInput(char keyStroke) {
        return new StringMessage(KEYBOARD_INPUT, String.valueOf(keyStroke));
    }

    public static ProjectEvent projectStarted(String projectId, String display) {
        return new ProjectEvent(Message.PROJECT_STARTED, projectId, display);
    }

    public static ProjectEvent projectStopped(String projectId, String display) {
        return new ProjectEvent(PROJECT_STOPPED, projectId, display);
    }

    public static Message mojoStarted(String projectId, String display) {
        return new ProjectEvent(Message.MOJO_STARTED, projectId, display);
    }

    public static ProjectEvent display(String projectId, String message) {
        return new ProjectEvent(Message.DISPLAY, projectId, message);
    }

}
