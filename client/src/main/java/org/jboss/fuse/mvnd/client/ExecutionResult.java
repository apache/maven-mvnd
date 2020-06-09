package org.jboss.fuse.mvnd.client;

/**
 * A result of a {@code mvnd} build.
 */
public interface ExecutionResult {

    boolean isSuccess();

    ExecutionResult assertFailure();

    ExecutionResult assertSuccess();

}
