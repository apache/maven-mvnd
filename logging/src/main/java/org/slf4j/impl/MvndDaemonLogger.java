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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public class MvndDaemonLogger extends MvndBaseLogger {

    final DateTimeFormatter dateTimeFormatter =
            new DateTimeFormatterBuilder().appendPattern("HH:mm:ss.SSS").toFormatter();

    PrintStream printStream;

    public MvndDaemonLogger(String name) {
        super(name);
    }

    @Override
    protected void doLog(int level, String message, Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.append(dateTimeFormatter.format(LocalTime.now()));
        pw.append(" ");
        switch (level) {
            case LOG_LEVEL_ERROR:
                pw.append("E");
                break;
            case LOG_LEVEL_WARN:
                pw.append("W");
                break;
            case LOG_LEVEL_INFO:
                pw.append("I");
                break;
            case LOG_LEVEL_DEBUG:
                pw.append("D");
                break;
            case LOG_LEVEL_TRACE:
                pw.append("T");
                break;
        }
        pw.append(" ");
        pw.append(message);
        if (t != null) {
            t.printStackTrace(pw);
        }
        PrintStream printStream = MvndSimpleLogger.CONFIG_PARAMS.outputChoice.getTargetPrintStream();
        printStream.println(sw);
        printStream.flush();
    }
}
