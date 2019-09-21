package org.jboss.fuse.mvnd.builder;

import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Test;

public class ReactorBuildQueueTest extends AbstractSmartBuilderTest {

    @Test
    public void testBasic() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        graph.addDependency(b, a);

        ReactorBuildQueue schl = new ReactorBuildQueue(graph.getSortedProjects(), graph);

        assertProjects(schl.getRootProjects(), a, c);
        Assert.assertFalse(schl.isEmpty());

        assertProjects(schl.onProjectFinish(a), b);
        Assert.assertTrue(schl.isEmpty());
    }

    @Test
    public void testNoDependencies() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);

        ReactorBuildQueue schl = new ReactorBuildQueue(graph.getSortedProjects(), graph);

        assertProjects(schl.getRootProjects(), a, b, c);
        Assert.assertTrue(schl.isEmpty());
    }

    @Test
    public void testMultipleUpstreamDependencies() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        graph.addDependency(b, a);
        graph.addDependency(b, c);

        ReactorBuildQueue schl = new ReactorBuildQueue(graph.getSortedProjects(), graph);

        assertProjects(schl.getRootProjects(), a, c);
        Assert.assertFalse(schl.isEmpty());

        assertProjects(schl.onProjectFinish(a), new MavenProject[0]);
        Assert.assertFalse(schl.isEmpty());

        assertProjects(schl.onProjectFinish(c), b);
        Assert.assertTrue(schl.isEmpty());
    }
}
