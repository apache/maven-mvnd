/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.mvndaemon.mvnd.builder;

import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.maven.lifecycle.internal.BuildThreadFactory;
import org.apache.maven.project.MavenProject;

/**
 * {@link ThreadPoolExecutor} wrapper.
 * <p>
 * Uses {@link PriorityBlockingQueue} and provided {@link Comparator} to order queue
 * {@link ProjectRunnable} tasks.
 *
 * File origin:
 * https://github.com/takari/takari-smart-builder/blob/takari-smart-builder-0.6.1/src/main/java/io/takari/maven/builder/smart/ProjectExecutorService.java
 */
class ProjectExecutorService {

    private final ExecutorService executor;
    private final BlockingQueue<Future<MavenProject>> completion = new LinkedBlockingQueue<>();
    private final Comparator<Runnable> taskComparator;

    public ProjectExecutorService(final int degreeOfConcurrency, final Comparator<MavenProject> projectComparator) {

        this.taskComparator = Comparator.comparing(r -> ((ProjectRunnable) r).getProject(), projectComparator);

        final BlockingQueue<Runnable> executorWorkQueue =
                new PriorityBlockingQueue<>(degreeOfConcurrency, taskComparator);

        executor =
                new ThreadPoolExecutor(
                        degreeOfConcurrency, // corePoolSize
                        degreeOfConcurrency, // maximumPoolSize
                        0L,
                        TimeUnit.MILLISECONDS, // keepAliveTime, unit
                        executorWorkQueue, // workQueue
                        new BuildThreadFactory() // threadFactory
                        ) {

                    @Override
                    protected void beforeExecute(Thread t, Runnable r) {
                        ProjectExecutorService.this.beforeExecute(t, r);
                    }
                };
    }

    public void submitAll(final Collection<? extends ProjectRunnable> tasks) {
        // when there are available worker threads, tasks are immediately executed, i.e. bypassed the
        // ordered queued. need to sort tasks, such that submission order matches desired execution
        // order
        tasks.stream().sorted(taskComparator).map(ProjectFutureTask::new).forEach(executor::execute);
    }

    /**
     * Returns {@link MavenProject} corresponding to the next completed task, waiting if none are yet
     * present.
     */
    public MavenProject take() throws InterruptedException, ExecutionException {
        return completion.take().get();
    }

    public void shutdown() {
        executor.shutdown();
    }

    public void cancel() {
        executor.shutdownNow();
    }

    // hook to allow pausing executor during unit tests
    protected void beforeExecute(Thread t, Runnable r) {}

    // for testing purposes only
    public void awaitShutdown() throws InterruptedException {
        executor.shutdown();
        while (!executor.awaitTermination(5, TimeUnit.SECONDS))
            ;
    }

    static interface ProjectRunnable extends Runnable {
        public MavenProject getProject();
    }

    private class ProjectFutureTask extends FutureTask<MavenProject> implements ProjectRunnable {
        private ProjectRunnable task;

        public ProjectFutureTask(ProjectRunnable task) {
            super(task, task.getProject());
            this.task = task;
        }

        @Override
        protected void done() {
            completion.add(this);
        }

        @Override
        public MavenProject getProject() {
            return task.getProject();
        }
    }
}
