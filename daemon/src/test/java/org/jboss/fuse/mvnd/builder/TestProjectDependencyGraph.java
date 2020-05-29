package org.jboss.fuse.mvnd.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;

public class TestProjectDependencyGraph implements ProjectDependencyGraph {

    private final List<MavenProject> projects = new ArrayList<MavenProject>();

    private final ListMultimap<MavenProject, MavenProject> downstream = ArrayListMultimap.create();

    private final ListMultimap<MavenProject, MavenProject> upstream = ArrayListMultimap.create();

    public TestProjectDependencyGraph(MavenProject... projects) {
        if (projects != null) {
            this.projects.addAll(Arrays.asList(projects));
        }
    }

    @Override
    public List<MavenProject> getAllProjects() {
        return projects;
    }

    @Override
    public List<MavenProject> getSortedProjects() {
        return projects;
    }

    @Override
    public List<MavenProject> getDownstreamProjects(MavenProject project, boolean transitive) {
        Assertions.assertFalse(transitive, "not implemented");
        return downstream.get(project);
    }

    @Override
    public List<MavenProject> getUpstreamProjects(MavenProject project, boolean transitive) {
        Assertions.assertFalse(transitive, "not implemented");
        return upstream.get(project);
    }

    public void addProject(MavenProject project) {
        projects.add(project);
    }

    public void addDependency(MavenProject from, MavenProject to) {
        // 'from' depends on 'to'
        // 'from' is a downstream dependency of 'to'
        // 'to' is upstream dependency of 'from'
        this.upstream.put(from, to);
        this.downstream.put(to, from);
    }
}
