package org.jboss.fuse.mvnd.builder;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReactorBuildQueueTest extends AbstractSmartBuilderTest {

    @Test
    public void testBasic() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        graph.addDependency(b, a);
        DependencyGraph<MavenProject> dp = DependencyGraph.fromMaven(graph, null);

        ReactorBuildQueue schl = new ReactorBuildQueue(graph.getSortedProjects(), dp);

        assertProjects(schl.getRootProjects(), a, c);
        Assertions.assertFalse(schl.isEmpty());

        assertProjects(schl.onProjectFinish(a), b);
        Assertions.assertTrue(schl.isEmpty());
    }

    @Test
    public void testNoDependencies() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        DependencyGraph<MavenProject> dp = DependencyGraph.fromMaven(graph, null);

        ReactorBuildQueue schl = new ReactorBuildQueue(graph.getSortedProjects(), dp);

        assertProjects(schl.getRootProjects(), a, b, c);
        Assertions.assertTrue(schl.isEmpty());
    }

    @Test
    public void testMultipleUpstreamDependencies() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        graph.addDependency(b, a);
        graph.addDependency(b, c);
        DependencyGraph<MavenProject> dp = DependencyGraph.fromMaven(graph, null);

        ReactorBuildQueue schl = new ReactorBuildQueue(graph.getSortedProjects(), dp);

        assertProjects(schl.getRootProjects(), a, c);
        Assertions.assertFalse(schl.isEmpty());

        assertProjects(schl.onProjectFinish(a), new MavenProject[0]);
        Assertions.assertFalse(schl.isEmpty());

        assertProjects(schl.onProjectFinish(c), b);
        Assertions.assertTrue(schl.isEmpty());
    }
}
