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
package org.mvndaemon.mvnd.daemon;

import java.util.Collection;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.mvndaemon.mvnd.builder.DependencyGraph;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.common.Message.BuildException;
import org.mvndaemon.mvnd.common.Message.BuildStarted;
import org.mvndaemon.mvnd.logging.smart.BuildEventListener;

/**
 * Sends events back to the client.
 */
public class ClientDispatcher extends BuildEventListener {
    private final Collection<Message> queue;

    public ClientDispatcher(Collection<Message> queue) {
        this.queue = queue;
    }

    public void sessionStarted(ExecutionEvent event) {
        final MavenSession session = event.getSession();
        final int degreeOfConcurrency = session.getRequest().getDegreeOfConcurrency();
        final DependencyGraph<MavenProject> dependencyGraph = DependencyGraph.fromMaven(session);
        session.getRequest().getData().put(DependencyGraph.class.getName(), dependencyGraph);

        final int maxThreads = degreeOfConcurrency == 1 ? 1 : dependencyGraph.computeMaxWidth(degreeOfConcurrency, 1000);
        queue.add(new BuildStarted(getCurrentProject(session).getName(), session.getProjects().size(), maxThreads));
    }

    public void projectStarted(ExecutionEvent event) {
        queue.add(Message.projectStarted(getProjectId(event), getProjectDisplay(event)));
    }

    public void projectLogMessage(String projectId, String event) {
        String msg = event.endsWith("\n") ? event.substring(0, event.length() - 1) : event;
        queue.add(projectId == null ? Message.log(msg) : Message.log(projectId, msg));
    }

    public void projectFinished(ExecutionEvent event) {
        queue.add(Message.projectStopped(getProjectId(event), getProjectDisplay(event)));
    }

    public void mojoStarted(ExecutionEvent event) {
        queue.add(Message.mojoStarted(getProjectId(event), getProjectDisplay(event)));
    }

    public void finish(int exitCode) throws Exception {
        queue.add(new Message.BuildFinished(exitCode));
        queue.add(Message.STOP_SINGLETON);
    }

    public void fail(Throwable t) throws Exception {
        queue.add(new BuildException(t));
        queue.add(Message.STOP_SINGLETON);
    }

    public void log(String msg) {
        queue.add(Message.log(msg));
    }

    private MavenProject getCurrentProject(MavenSession mavenSession) {
        // Workaround for https://issues.apache.org/jira/browse/MNG-6979
        // MavenSession.getCurrentProject() does not return the correct value in some cases
        String executionRootDirectory = mavenSession.getExecutionRootDirectory();
        if (executionRootDirectory == null) {
            return mavenSession.getCurrentProject();
        }
        return mavenSession.getProjects().stream()
                .filter(p -> (p.getFile() != null && executionRootDirectory.equals(p.getFile().getParent())))
                .findFirst()
                .orElse(mavenSession.getCurrentProject());
    }

    private String getProjectId(ExecutionEvent event) {
        return event.getProject().getArtifactId();
    }

    private String getProjectDisplay(ExecutionEvent event) {
        String projectId = getProjectId(event);
        return event.getMojoExecution() != null
                ? ":" + projectId + ":" + event.getMojoExecution().toString()
                : ":" + projectId;
    }

}
