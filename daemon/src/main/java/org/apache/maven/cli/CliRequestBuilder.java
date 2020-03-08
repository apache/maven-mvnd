package org.apache.maven.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.codehaus.plexus.classworlds.ClassWorld;

public class CliRequestBuilder {

    CliRequest request = new CliRequest( null, null );

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
