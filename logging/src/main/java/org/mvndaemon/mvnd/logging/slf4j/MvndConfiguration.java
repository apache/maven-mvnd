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

import org.apache.maven.cli.logging.Slf4jConfiguration;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

public class MvndConfiguration implements Slf4jConfiguration {
    @Override
    public void setRootLoggerLevel(Level level) {
        String value;
        switch (level) {
            case DEBUG:
                value = "debug";
                break;

            case INFO:
                value = "info";
                break;

            default:
                value = "error";
                break;
        }
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", value);
    }

    @Override
    public void activate() {
        ILoggerFactory lf = LoggerFactory.getILoggerFactory();
        if (lf instanceof MvndLoggerFactory) {
            ((MvndLoggerFactory) lf).reconfigure();
        }
    }
}
