package org.jboss.fuse.mvnd.logging.smart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.Display;

@Singleton
@Named
public class MavenLoggingSpy extends AbstractEventSpy {

    static MavenLoggingSpy instance;

    private Terminal terminal;
    private Display display;
    private Map<String, ProjectBuild> projects = new LinkedHashMap<>();
    private List<String> events;

    public MavenLoggingSpy() {
        instance = this;
    }

    @Override
    public void init(Context context) throws Exception {
        terminal = TerminalBuilder.terminal();
        projects = new LinkedHashMap<>();
        events = new ArrayList<>();
        display = new Display(terminal, false);
    }

    @Override
    public void close() throws Exception {
        display.update(Collections.emptyList(), 0);
        display = null;
        for (String event : events) {
            terminal.writer().print(event);
        }
        terminal.flush();
        terminal.close();
        events = null;
        projects = null;
    }

    @Override
    public void onEvent(Object event) throws Exception {
        if (event instanceof ExecutionEvent) {
            ExecutionEvent executionEvent = (ExecutionEvent) event;
            switch (executionEvent.getType()) {
                case SessionStarted:
                    notifySessionStart(executionEvent);
                    break;
                case SessionEnded:
                    notifySessionFinish(executionEvent);
                    break;
                case ProjectStarted:
                    notifyProjectBuildStart(executionEvent);
                    break;
                case ProjectSucceeded:
                case ProjectFailed:
                case ProjectSkipped:
                    notifyProjectBuildFinish(executionEvent);
                    break;
                case MojoStarted:
                    notifyMojoExecutionStart(executionEvent);
                    break;
                case MojoSucceeded:
                case MojoSkipped:
                case MojoFailed:
                    notifyMojoExecutionFinish(executionEvent);
                    break;
                default:
                    break;
            }
        }
    }

    void notifySessionStart(ExecutionEvent event) {
    }

    private void notifySessionFinish(ExecutionEvent event) {
    }

    private synchronized void notifyProjectBuildStart(ExecutionEvent event) {
        ProjectBuild pb = new ProjectBuild();
        pb.project = event.getProject();
        pb.execution = event.getMojoExecution();
        pb.events = new ArrayList<>();
        projects.put(event.getProject().getId(), pb);
        update();
    }

    private synchronized void notifyProjectBuildFinish(ExecutionEvent event) throws Exception {
        ProjectBuild pb = projects.remove(event.getProject().getId());
        events.addAll(pb.events);
        update();
    }

    private synchronized void notifyMojoExecutionStart(ExecutionEvent event) {
        projects.get(event.getProject().getId()).execution = event.getMojoExecution();
        update();
    }

    private synchronized void notifyMojoExecutionFinish(ExecutionEvent event) {
        projects.get(event.getProject().getId()).execution = null;
        update();
    }

    private void update() {
        Size size = terminal.getSize();
        display.resize(size.getRows(), size.getColumns());
        List<AttributedString> lines = new ArrayList<>();
        lines.add(new AttributedString("Building..."));
        for (ProjectBuild build : projects.values()) {
            lines.add(build.toDisplay());
        }
        display.update(lines, -1);
    }

    public void append(String projectId, String event) {
        ProjectBuild project = projectId != null ? projects.get(projectId) : null;
        if (project != null) {
            project.events.add(event);
        } else {
            events.add(event);
        }
    }

    private static class ProjectBuild {
        MavenProject project;
        volatile MojoExecution execution;
        List<String> events;

        @Override
        public String toString() {
            MojoExecution e = execution;
            return e != null ? ":" + project.getArtifactId() + ":" + e.toString() : ":" + project.getArtifactId();
        }

        public AttributedString toDisplay() {
            return new AttributedString(toString());
        }
    }

}
