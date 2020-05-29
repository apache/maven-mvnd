package org.jboss.fuse.mvnd.daemon;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Local paths relevant for the {@link Client}.
 */
public class ClientLayout {

    private final Path localMavenRepository;
    private final Path settings;

    public ClientLayout(Path localMavenRepository, Path settings) {
        super();
        this.localMavenRepository = Objects.requireNonNull(localMavenRepository, "localMavenRepository");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public Path getLocalMavenRepository() {
        return localMavenRepository;
    }

    public Path getSettings() {
        return settings;
    }

}
