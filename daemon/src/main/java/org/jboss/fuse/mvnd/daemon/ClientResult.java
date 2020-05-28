package org.jboss.fuse.mvnd.daemon;

import java.util.ArrayList;
import java.util.List;

/**
 * A result of a {@code mvnd} build.
 *
 * @param <O> the type of the {@link ClientOutput}.
 */
public class ClientResult<O extends ClientOutput> {

    private final boolean success;
    private final O clientOutput;
    private final List<String> args;

    public ClientResult(List<String> args, boolean success, O clientOutput) {
        super();
        this.args = new ArrayList<>(args);
        this.success = success;
        this.clientOutput = clientOutput;
    }

    public ClientResult<O> assertSuccess() {
        if (!this.success) {
            throw new AssertionError(appendCommand(new StringBuilder("Build failed: ")));
        }
        return this;
    }

    public ClientResult<O> assertFailure() {
        if (this.success) {
            throw new AssertionError(appendCommand(new StringBuilder("Build did not fail: ")));
        }
        return this;
    }

    public O getClientOutput() {
        return clientOutput;
    }

    public boolean isSuccess() {
        return success;
    }

    StringBuilder appendCommand(StringBuilder sb) {
        sb.append("mvnd");
        for (String arg : args) {
            sb.append(" \"").append(arg).append('"');
        }
        return sb;

    }
}
