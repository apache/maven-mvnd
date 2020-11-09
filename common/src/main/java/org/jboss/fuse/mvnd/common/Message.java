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
package org.jboss.fuse.mvnd.common;

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

public abstract class Message {
    static final int BUILD_REQUEST = 0;
    static final int BUILD_EVENT = 1;
    static final int BUILD_MESSAGE = 2;
    static final int BUILD_EXCEPTION = 3;
    static final int KEEP_ALIVE = 4;
    static final int STOP = 5;
    static final int BUILD_STARTED = 6;
    static final int DISPLAY = 7;
    static final int PROMPT = 8;
    static final int PROMPT_RESPONSE = 9;
    static final int CANCEL = 10;

    public static final SimpleMessage KEEP_ALIVE_SINGLETON = new SimpleMessage(Message.KEEP_ALIVE, "KEEP_ALIVE");
    public static final SimpleMessage STOP_SINGLETON = new SimpleMessage(Message.STOP, "STOP");
    public static final SimpleMessage CANCEL_SINGLETON = new SimpleMessage(Message.CANCEL, "CANCEL");

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
        case BUILD_EVENT:
            return BuildEvent.read(input);
        case BUILD_MESSAGE:
            return BuildMessage.read(input);
        case BUILD_EXCEPTION:
            return BuildException.read(input);
        case KEEP_ALIVE:
            return SimpleMessage.KEEP_ALIVE_SINGLETON;
        case STOP:
            return SimpleMessage.STOP_SINGLETON;
        case DISPLAY:
            return Display.read(input);
        case PROMPT:
            return Prompt.read(input);
        case PROMPT_RESPONSE:
            return PromptResponse.read(input);
        case CANCEL:
            return SimpleMessage.CANCEL_SINGLETON;
        }
        throw new IllegalStateException("Unexpected message type: " + type);
    }

    final long timestamp = System.nanoTime();

    public long timestamp() {
        return timestamp;
    }

    public abstract void write(DataOutputStream output) throws IOException;

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
            output.write(BUILD_REQUEST);
            writeStringList(output, args);
            writeUTF(output, workingDir);
            writeUTF(output, projectDir);
            writeStringMap(output, env);
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
            output.write(BUILD_EXCEPTION);
            writeUTF(output, message);
            writeUTF(output, className);
            writeUTF(output, stackTrace);
        }
    }

    public static class BuildEvent extends Message {
        public enum Type {
            BuildStopped, ProjectStarted, ProjectStopped, MojoStarted
        }

        final Type type;
        final String projectId;
        final String display;

        public static Message read(DataInputStream input) throws IOException {
            BuildEvent.Type type = BuildEvent.Type.values()[input.read()];
            String projectId = readUTF(input);
            String display = readUTF(input);
            return new BuildEvent(type, projectId, display);
        }

        public BuildEvent(Type type, String projectId, String display) {
            this.type = type;
            this.projectId = projectId;
            this.display = display;
        }

        public Type getType() {
            return type;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getDisplay() {
            return display;
        }

        @Override
        public String toString() {
            return "BuildEvent{" +
                    "projectId='" + projectId + '\'' +
                    ", type=" + type +
                    ", display='" + display + '\'' +
                    '}';
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.write(BUILD_EVENT);
            output.write(type.ordinal());
            writeUTF(output, projectId);
            writeUTF(output, display);
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
            return "BuildEvent{" +
                    "projectId='" + projectId + '\'' +
                    ", projectCount=" + projectCount +
                    ", maxThreads='" + maxThreads + '\'' +
                    '}';
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.write(BUILD_STARTED);
            writeUTF(output, projectId);
            output.writeInt(projectCount);
            output.writeInt(maxThreads);
        }
    }

    public static class BuildMessage extends Message {
        final String projectId;
        final String message;

        public static Message read(DataInputStream input) throws IOException {
            String projectId = readUTF(input);
            String message = readUTF(input);
            return new BuildMessage(projectId.isEmpty() ? null : projectId, message);
        }

        public BuildMessage(String projectId, String message) {
            this.projectId = projectId;
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public String getProjectId() {
            return projectId;
        }

        @Override
        public String toString() {
            return "BuildMessage{" +
                    "projectId='" + projectId + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.write(BUILD_MESSAGE);
            writeUTF(output, projectId != null ? projectId : "");
            writeUTF(output, message);
        }
    }

    public static class SimpleMessage extends Message {

        final int type;
        final String mnemonic;

        /**
         * Use {@link #KEEP_ALIVE_SINGLETON}
         *
         * @param type
         */
        private SimpleMessage(int type, String mnemonic) {
            this.type = type;
            this.mnemonic = mnemonic;
        }

        @Override
        public String toString() {
            return mnemonic;
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.write(type);
        }
    }

    public static class Display extends Message {

        final String projectId;
        final String message;

        public static Message read(DataInputStream input) throws IOException {
            String projectId = readUTF(input);
            String message = readUTF(input);
            return new Display(projectId, message);
        }

        public Display(String projectId, String message) {
            this.projectId = projectId;
            this.message = message;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "Display{" +
                    "projectId='" + projectId + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.write(DISPLAY);
            writeUTF(output, projectId);
            writeUTF(output, message);
        }
    }

    public static class Prompt extends Message {

        final String projectId;
        final String uid;
        final String message;
        final boolean password;

        public static Message read(DataInputStream input) throws IOException {
            String projectId = Message.readUTF(input);
            String uid = Message.readUTF(input);
            String message = Message.readUTF(input);
            boolean password = input.readBoolean();
            return new Prompt(projectId, uid, message, password);
        }

        public Prompt(String projectId, String uid, String message, boolean password) {
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
            output.write(PROMPT);
            writeUTF(output, projectId);
            writeUTF(output, uid);
            writeUTF(output, message);
            output.writeBoolean(password);
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

        public PromptResponse(String projectId, String uid, String message) {
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
            output.write(PROMPT_RESPONSE);
            writeUTF(output, projectId);
            writeUTF(output, uid);
            writeUTF(output, message);
        }
    }

}
