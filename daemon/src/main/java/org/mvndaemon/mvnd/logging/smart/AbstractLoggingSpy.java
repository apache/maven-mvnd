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
package org.mvndaemon.mvnd.logging.smart;

import java.util.Collection;
import java.util.function.Consumer;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.mvndaemon.mvnd.builder.DependencyGraph;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.common.Message.BuildException;
import org.mvndaemon.mvnd.common.Message.BuildStarted;

/**
 * Sends events back to the client.
 */
public class AbstractLoggingSpy {
    private static final AbstractLoggingSpy DUMMY = new AbstractLoggingSpy(m -> {
    });
    private final Consumer<Message> queue;

    /**
     * @return a dummy {@link AbstractLoggingSpy} that just swallows the messages and does not send them anywhere
     */
    public static AbstractLoggingSpy dummy() {
        return DUMMY;
    }

    public AbstractLoggingSpy(Collection<Message> queue) {
        this(queue::add);
    }

    public AbstractLoggingSpy(Consumer<Message> queue) {
        this.queue = queue;
    }

    public void sessionStarted(ExecutionEvent event) {
        final MavenSession session = event.getSession();
        final int degreeOfConcurrency = session.getRequest().getDegreeOfConcurrency();
        final DependencyGraph<MavenProject> dependencyGraph = DependencyGraph.fromMaven(session);
        session.getRequest().getData().put(DependencyGraph.class.getName(), dependencyGraph);

        final int maxThreads = degreeOfConcurrency == 1 ? 1 : dependencyGraph.computeMaxWidth(degreeOfConcurrency, 1000);
        queue.accept(new BuildStarted(getCurrentProject(session).getName(), session.getProjects().size(), maxThreads));
    }

    public void projectStarted(ExecutionEvent event) {
        queue.accept(Message.projectStarted(getProjectId(event), getProjectDisplay(event)));
    }

    public void projectLogMessage(String projectId, String event) {
        String msg = event.endsWith("\n") ? event.substring(0, event.length() - 1) : event;
        queue.accept(projectId == null ? Message.log(msg) : Message.log(projectId, msg));
    }

    public void projectFinished(ExecutionEvent event) {
        queue.accept(Message.projectStopped(getProjectId(event), getProjectDisplay(event)));
    }

    public void mojoStarted(ExecutionEvent event) {
        queue.accept(Message.mojoStarted(getProjectId(event), getProjectDisplay(event)));
    }

    public void finish(int exitCode) throws Exception {
        queue.accept(new Message.BuildFinished(exitCode));
        queue.accept(Message.STOP_SINGLETON);
    }

    public void fail(Throwable t) throws Exception {
        queue.accept(new BuildException(t));
        queue.accept(Message.STOP_SINGLETON);
    }

    public void log(String msg) {
        queue.accept(Message.log(msg));
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
