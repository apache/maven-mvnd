package org.jboss.fuse.mvnd.client;

import java.util.Arrays;
import java.util.List;

public interface Client {

    ExecutionResult execute(ClientOutput output, List<String> args) throws InterruptedException;

    default ExecutionResult execute(ClientOutput output, String... args) throws InterruptedException {
        return execute(output, Arrays.asList(args));
    }

}
