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

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import org.apache.maven.slf4j.MavenBaseLogger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

public class MvndDaemonLogger extends MavenBaseLogger {

    final DateTimeFormatter dateTimeFormatter =
            new DateTimeFormatterBuilder().appendPattern("HH:mm:ss.SSS").toFormatter();

    public MvndDaemonLogger(String name) {
        super(name);
    }

    @Override
    protected String renderLevel(int levelInt) {
        switch (levelInt) {
            case LOG_LEVEL_ERROR:
                return "E";
            case LOG_LEVEL_WARN:
                return "W";
            case LOG_LEVEL_INFO:
                return "I";
            case LOG_LEVEL_DEBUG:
                return "D";
            case LOG_LEVEL_TRACE:
                return "T";
        }
        throw new IllegalStateException("Unrecognized level [" + levelInt + "]");
    }

    @Override
    protected void handleNormalizedLoggingCall(
            Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable) {
        StringBuilder buf = new StringBuilder(32);
        buf.append(dateTimeFormatter.format(LocalTime.now()));
        buf.append(" ");
        buf.append(renderLevel(level.toInt()));
        buf.append(" ");
        String message = MessageFormatter.basicArrayFormat(messagePattern, arguments);
        buf.append(message);
        write(buf, throwable);
    }
}
