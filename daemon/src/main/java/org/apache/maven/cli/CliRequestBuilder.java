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
package org.apache.maven.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.codehaus.plexus.classworlds.ClassWorld;

public class CliRequestBuilder {

    CliRequest request = new CliRequest(null, null);

    public CliRequestBuilder arguments(List<String> arguments) {
        request.args = arguments.toArray(new String[0]);
        return this;
    }

    public CliRequestBuilder classWorld(ClassWorld classWorld) {
        request.classWorld = classWorld;
        return this;
    }

    public CliRequestBuilder workingDirectory(Path workingDirectory) {
        request.workingDirectory = workingDirectory.toAbsolutePath().toString();
        return this;
    }

    public CliRequestBuilder projectDirectory(Path projectDirectory) {
        request.multiModuleProjectDirectory = projectDirectory.toAbsolutePath().toFile();
        return this;
    }

    public CliRequestBuilder debug(boolean debug) {
        request.debug = debug;
        return this;
    }

    public CliRequestBuilder quiet(boolean quiet) {
        request.quiet = quiet;
        return this;
    }

    public CliRequestBuilder showErrors(boolean showErrors) {
        request.showErrors = showErrors;
        return this;
    }

    public CliRequestBuilder userProperties(Properties userProperties) {
        request.userProperties = userProperties;
        return this;
    }

    public CliRequestBuilder systemProperties(Properties systemProperties) {
        request.systemProperties = systemProperties;
        return this;
    }

    public CliRequest build() {
        return request;
    }

}
