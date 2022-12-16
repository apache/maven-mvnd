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
package org.mvndaemon.mvnd.client;

import org.apache.maven.cli.MavenCli;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DefaultClient {
    public static void main(String[] argv) throws Exception {
        final String logbackConfFallback = System.getProperty("logback.configurationFile.fallback");
        if (null != logbackConfFallback && !"".equals(logbackConfFallback)) {
            System.setProperty("logback.configurationFile", logbackConfFallback);
            System.clearProperty("logback.configurationFile.fallback");
        }

        final Logger LOGGER = LoggerFactory.getLogger(DefaultClient.class);
	    LOGGER.warn("Found old JDK, fallback to the embedded maven!");
	    LOGGER.warn("Use JDK 11+ to run maven-mvnd client!");

        MavenCli.main(argv);
    }
}
