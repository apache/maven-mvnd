package org.jboss.fuse.mvnd.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.util.concurrent.Monitor;
import org.apache.maven.project.MavenProject;
import org.jboss.fuse.mvnd.builder.ProjectExecutorService.ProjectRunnable;
import org.junit.Assert;
import org.junit.Test;

import static org.jboss.fuse.mvnd.builder.ProjectComparator.id;

public class ProjectExecutorServiceTest extends AbstractSmartBuilderTest {

    @Test
    public void testBuildOrder() throws Exception {
        final MavenProject a = newProject("a");
        final MavenProject b = newProject("b");
        final MavenProject c = newProject("c");
        TestProjectDependencyGraph graph = new TestProjectDependencyGraph(a, b, c);
        graph.addDependency(b, a);

        HashMap<String, AtomicLong> serviceTimes = new HashMap<>();
        serviceTimes.put(id(a), new AtomicLong(1L));
        serviceTimes.put(id(b), new AtomicLong(1L));
        serviceTimes.put(id(c), new AtomicLong(3L));

        Comparator<MavenProject> cmp = org.jboss.fuse.mvnd.builder.ProjectComparator.create0(graph, serviceTimes, p -> id(p));

        PausibleProjectExecutorService executor = new PausibleProjectExecutorService(1, cmp);

        final List<MavenProject> executed = new ArrayList<>();

        class TestProjectRunnable implements ProjectRunnable {
            private final MavenProject project;

            TestProjectRunnable(MavenProject project) {
                this.project = project;
            }

            @Override
            public void run() {
                executed.add(project);
            }

            @Override
            public MavenProject getProject() {
                return project;
            }
        }

        // the executor has single work thread and is paused
        // first task execution is blocked because the executor is paused
        // the subsequent tasks are queued and thus queue order can be asserted

        // this one gets stuck on the worker thread
        executor.submitAll(Collections.singleton(new TestProjectRunnable(a)));

        // these are queued and ordered
        executor.submitAll(Arrays.asList(new TestProjectRunnable(a), new TestProjectRunnable(b),
                new TestProjectRunnable(c)));

        executor.resume();
        executor.awaitShutdown();

        Assert.assertEquals(Arrays.asList(a, c, a, b), executed);
    }

    // copy&paste from ThreadPoolExecutor javadoc (use of Guava is a nice touch there)
    private static class PausibleProjectExecutorService extends org.jboss.fuse.mvnd.builder.ProjectExecutorService {

        private final Monitor monitor = new Monitor();
        private boolean isPaused = true;
        private final Monitor.Guard paused = new Monitor.Guard(monitor) {
            @Override
            public boolean isSatisfied() {
                return isPaused;
            }
        };

        private final Monitor.Guard notPaused = new Monitor.Guard(monitor) {
            @Override
            public boolean isSatisfied() {
                return !isPaused;
            }
        };

        public PausibleProjectExecutorService(int degreeOfConcurrency,
                                              Comparator<MavenProject> projectComparator) {
            super(degreeOfConcurrency, projectComparator);
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            monitor.enterWhenUninterruptibly(notPaused);
            try {
                monitor.waitForUninterruptibly(notPaused);
            } finally {
                monitor.leave();
            }
        }

        public void resume() {
            monitor.enterIf(paused);
            try {
                isPaused = false;
            } finally {
                monitor.leave();
            }
        }
    }
}
