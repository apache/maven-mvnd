package org.jboss.fuse.mvnd.builder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.project.MavenProject;

interface DependencyGraph<K> {

    static DependencyGraph<MavenProject> fromMaven(ProjectDependencyGraph graph) {
        List<MavenProject> projects = graph.getAllProjects();
        Map<MavenProject, List<MavenProject>> upstreams = projects.stream()
                .collect(Collectors.toMap(p -> p, p -> graph.getUpstreamProjects(p, false)));
        Map<MavenProject, List<MavenProject>> downstreams = projects.stream()
                .collect(Collectors.toMap(p -> p, p -> graph.getDownstreamProjects(p, false)));
        return new DependencyGraph<MavenProject>() {
            @Override
            public Stream<MavenProject> getDownstreamProjects(MavenProject project) {
                return downstreams.get(project).stream();
            }

            @Override
            public Stream<MavenProject> getProjects() {
                return projects.stream();
            }

            @Override
            public Stream<MavenProject> getUpstreamProjects(MavenProject project) {
                return upstreams.get(project).stream();
            }

            @Override
            public boolean isRoot(MavenProject project) {
                return upstreams.get(project).isEmpty();
            }
        };
    }

    Stream<K> getProjects();

    boolean isRoot(K project);

    Stream<K> getDownstreamProjects(K project);

    Stream<K> getUpstreamProjects(K project);

}
