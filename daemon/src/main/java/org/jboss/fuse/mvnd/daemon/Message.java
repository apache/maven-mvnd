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
package org.jboss.fuse.mvnd.daemon;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.plexus.util.ExceptionUtils;

public abstract class Message {

    final long timestamp = System.nanoTime();

    long timestamp() {
        return timestamp;
    }

    public static class BuildRequest extends Message {
        final List<String> args;
        final String workingDir;
        final String projectDir;

        public BuildRequest(List<String> args, String workingDir, String projectDir) {
            this.args = args;
            this.workingDir = workingDir;
            this.projectDir = projectDir;
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

        @Override
        public String toString() {
            return "BuildRequest{" +
                    "args=" + args +
                    ", workingDir='" + workingDir + '\'' +
                    ", projectDir='" + projectDir + '\'' +
                    '}';
        }
    }

    public static class BuildException extends Message {
        final String message;
        final String className;
        final String stackTrace;

        public BuildException(Throwable t) {
            this(t.getMessage(), t.getClass().getName(), ExceptionUtils.getStackTrace(t));
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
    }

    public static class BuildEvent extends Message {
        enum Type {
            BuildStarted, BuildStopped, ProjectStarted, ProjectStopped, MojoStarted, MojoStopped
        }
        final Type type;
        final String projectId;
        final String display;

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
                    "type=" + type +
                    ", display='" + display + '\'' +
                    '}';
        }
    }

    public static class BuildMessage extends Message {
        final String message;

        public BuildMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "BuildMessage{" +
                    "message='" + message + '\'' +
                    '}';
        }
    }

    public static class MessageSerializer implements Serializer<Message> {

        final int BUILD_REQUEST = 0;
        final int BUILD_EVENT = 1;
        final int BUILD_MESSAGE = 2;
        final int BUILD_EXCEPTION = 3;

        @Override
        public Message read(DataInputStream input) throws EOFException, Exception {
            int type = input.read();
            if (type == -1) {
                return null;
            }
            switch (type) {
                case BUILD_REQUEST:
                    return readBuildRequest(input);
                case BUILD_EVENT:
                    return readBuildEvent(input);
                case BUILD_MESSAGE:
                    return readBuildMessage(input);
                case BUILD_EXCEPTION:
                    return readBuildException(input);
            }
            throw new IllegalStateException("Unexpected message type: " + type);
        }

        @Override
        public void write(DataOutputStream output, Message value) throws Exception {
            if (value instanceof BuildRequest) {
                output.write(BUILD_REQUEST);
                writeBuildRequest(output, (BuildRequest) value);
            } else if (value instanceof BuildEvent) {
                output.write(BUILD_EVENT);
                writeBuildEvent(output, (BuildEvent) value);
            } else if (value instanceof BuildMessage) {
                output.write(BUILD_MESSAGE);
                writeBuildMessage(output, (BuildMessage) value);
            } else if (value instanceof BuildException) {
                output.write(BUILD_EXCEPTION);
                writeBuildException(output, (BuildException) value);
            } else {
                throw new IllegalStateException();
            }
        }

        private BuildRequest readBuildRequest(DataInputStream input) throws IOException {
            List<String> args = readStringList(input);
            String workingDir = readUTF(input);
            String projectDir = readUTF(input);
            return new BuildRequest(args, workingDir, projectDir);
        }

        private void writeBuildRequest(DataOutputStream output, BuildRequest value) throws IOException {
            writeStringList(output, value.args);
            writeUTF(output, value.workingDir);
            writeUTF(output, value.projectDir);
        }

        private BuildEvent readBuildEvent(DataInputStream input) throws IOException {
            BuildEvent.Type type = BuildEvent.Type.values()[input.read()];
            String projectId = readUTF(input);
            String display = readUTF(input);
            return new BuildEvent(type, projectId, display);
        }

        private void writeBuildEvent(DataOutputStream output, BuildEvent value) throws IOException {
            output.write(value.type.ordinal());
            writeUTF(output, value.projectId);
            writeUTF(output, value.display);
        }

        private BuildMessage readBuildMessage(DataInputStream input) throws IOException {
            String message = readUTF(input);
            return new BuildMessage(message);
        }

        private void writeBuildMessage(DataOutputStream output, BuildMessage value) throws IOException {
            writeUTF(output, value.message);
        }

        private BuildException readBuildException(DataInputStream input) throws IOException {
            String message = readUTF(input);
            String className = readUTF(input);
            String stackTrace = readUTF(input);
            return new BuildException(message, className, stackTrace);
        }

        private void writeBuildException(DataOutputStream output, BuildException value) throws IOException {
            writeUTF(output, value.message);
            writeUTF(output, value.className);
            writeUTF(output, value.stackTrace);
        }

        private List<String> readStringList(DataInputStream input) throws IOException {
            ArrayList<String> l = new ArrayList<>();
            int nb = input.readInt();
            for (int i = 0; i < nb; i++) {
                l.add(readUTF(input));
            }
            return l;
        }

        private void writeStringList(DataOutputStream output, List<String> value) throws IOException {
            output.writeInt(value.size());
            for (String v : value) {
                writeUTF(output, v);
            }
        }

        private static final String INVALID_BYTE = "Invalid byte";
        private static final int UTF_BUFS_CHAR_CNT = 256;
        private static final int UTF_BUFS_BYTE_CNT = UTF_BUFS_CHAR_CNT * 3;
        final byte[] byteBuf = new byte[UTF_BUFS_BYTE_CNT];

        String readUTF(DataInputStream input) throws IOException {
            int len = input.readInt();
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
                    chars[charIdx ++] = (char) a;
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
                    final int b = byteBuf[i ++] & 0xff;
                    if ((b & 0xc0) != 0x80) {
                        throw new UTFDataFormatException(INVALID_BYTE);
                    }
                    chars[charIdx ++] = (char) ((a & 0x1f) << 6 | b & 0x3f);
                } else if (a < 0xf0) {
                    if (i == cnt) {
                        cnt = input.read(byteBuf, 0, Math.min(UTF_BUFS_BYTE_CNT, len - charIdx));
                        if (cnt < 0) {
                            throw new EOFException();
                        }
                        i = 0;
                    }
                    final int b = byteBuf[i ++] & 0xff;
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
                    final int c = byteBuf[i ++] & 0xff;
                    if ((c & 0xc0) != 0x80) {
                        throw new UTFDataFormatException(INVALID_BYTE);
                    }
                    chars[charIdx ++] = (char) ((a & 0x0f) << 12 | (b & 0x3f) << 6 | c & 0x3f);
                } else {
                    throw new UTFDataFormatException(INVALID_BYTE);
                }
            }
            return String.valueOf(chars);
        }

        void writeUTF(DataOutputStream output, String s) throws IOException {
            final int length = s.length();
            output.writeInt(length);
            int strIdx = 0;
            int byteIdx = 0;
            while (strIdx < length) {
                final char c = s.charAt(strIdx ++);
                if (c > 0 && c <= 0x7f) {
                    byteBuf[byteIdx ++] = (byte) c;
                } else if (c <= 0x07ff) {
                    byteBuf[byteIdx ++] = (byte)(0xc0 | 0x1f & c >> 6);
                    byteBuf[byteIdx ++] = (byte)(0x80 | 0x3f & c);
                } else {
                    byteBuf[byteIdx ++] = (byte)(0xe0 | 0x0f & c >> 12);
                    byteBuf[byteIdx ++] = (byte)(0x80 | 0x3f & c >> 6);
                    byteBuf[byteIdx ++] = (byte)(0x80 | 0x3f & c);
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

    }
}
