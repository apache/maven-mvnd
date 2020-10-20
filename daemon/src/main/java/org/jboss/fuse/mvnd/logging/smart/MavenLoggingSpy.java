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
package org.jboss.fuse.mvnd.logging.smart;

import java.io.IOError;
import org.apache.maven.execution.MavenSession;
import org.jboss.fuse.mvnd.common.logging.TerminalOutput;

public class MavenLoggingSpy extends AbstractLoggingSpy {

    private TerminalOutput output;

    public MavenLoggingSpy() {
    }

    @Override
    protected void onStartSession(MavenSession session) {
        try {
            output = new TerminalOutput(null);
            output.startBuild(
                    session.getTopLevelProject().getName(),
                    session.getAllProjects().size(),
                    session.getRequest().getDegreeOfConcurrency());
        } catch (Exception e) {
            throw new IOError(e);
        }
    }

    @Override
    protected void onFinishSession() {
        try {
            output.close();
        } catch (Exception e) {
            throw new IOError(e);
        }
    }

    @Override
    protected void onStartProject(String projectId, String display) {
        super.onStartProject(projectId, display);
        output.projectStateChanged(projectId, display);
    }

    @Override
    protected void onStopProject(String projectId, String display) {
        output.projectFinished(projectId);
    }

    @Override
    protected void onStartMojo(String projectId, String display) {
        super.onStartMojo(projectId, display);
        output.projectStateChanged(projectId, display);
    }

    @Override
    protected void onStopMojo(String projectId, String display) {
        output.projectStateChanged(projectId, ":" + projectId);
        super.onStopMojo(projectId, display);
    }

    @Override
    protected void onProjectLog(String projectId, String message) {
        super.onProjectLog(projectId, message);
        output.accept(projectId, message);
    }

}
