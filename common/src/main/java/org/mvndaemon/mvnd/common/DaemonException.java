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

public class DaemonException extends RuntimeException {

    public DaemonException(String message) {
        super(message);
    }

    public DaemonException(String message, Throwable cause) {
        super(message, cause);
    }

    public DaemonException(Throwable cause) {
        super(cause);
    }

    public static class InterruptedException extends DaemonException {
        public InterruptedException(Throwable cause) {
            super(cause);
        }
    }

    public static class ConnectException extends DaemonException {
        public ConnectException(String message) {
            super(message);
        }

        public ConnectException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class StartException extends DaemonException {
        public StartException(String message) {
            super(message);
        }

        public StartException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class MessageIOException extends DaemonException {
        public MessageIOException(String message) {
            super(message);
        }

        public MessageIOException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class RecoverableMessageIOException extends MessageIOException {
        public RecoverableMessageIOException(String message) {
            super(message);
        }

        public RecoverableMessageIOException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class StaleAddressException extends DaemonException {
        public StaleAddressException(String message) {
            super(message);
        }

        public StaleAddressException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
