package org.jboss.fuse.mvnd.builder;

import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.jboss.fuse.mvnd.builder.ProjectComparator.id;

public class ProjectComparatorTest extends AbstractSmartBuilderTest {

    @Test
    public void testPriorityQueueOrder() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        graph.addDependency(b, a);
        DependencyGraph<MavenProject> dp = DependencyGraph.fromMaven(graph, null);

        Comparator<MavenProject> cmp = ProjectComparator.create0(dp, new HashMap<>(), ProjectComparator::id);

        Queue<MavenProject> queue = new PriorityQueue<>(3, cmp);
        queue.add(a);
        queue.add(b);
        queue.add(c);

        Assertions.assertEquals(a, queue.poll());
        Assertions.assertEquals(c, queue.poll());
        Assertions.assertEquals(b, queue.poll());
    }

    @Test
    public void testPriorityQueueOrder_historicalServiceTimes() {
        MavenProject a = newProject("a"), b = newProject("b"), c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        graph.addDependency(b, a);
        DependencyGraph<MavenProject> dp = DependencyGraph.fromMaven(graph, null);

        HashMap<String, AtomicLong> serviceTimes = new HashMap<>();
        serviceTimes.put(id(a), new AtomicLong(1L));
        serviceTimes.put(id(b), new AtomicLong(1L));
        serviceTimes.put(id(c), new AtomicLong(3L));

        Comparator<MavenProject> cmp = ProjectComparator.create0(dp, serviceTimes, ProjectComparator::id);

        Queue<MavenProject> queue = new PriorityQueue<>(3, cmp);
        queue.add(a);
        queue.add(b);
        queue.add(c);

        Assertions.assertEquals(c, queue.poll());
        Assertions.assertEquals(a, queue.poll());
        Assertions.assertEquals(b, queue.poll());
    }

}
