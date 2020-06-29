/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.fuse.mvnd.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.enterprise.inject.Default;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.PluginRealmCache;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default PluginCache implementation. Assumes cached data does not change.
 */
@Singleton
@Named
@Default
public class CliPluginRealmCache
    implements PluginRealmCache, Disposable
{
    /**
     * CacheKey
     */
    protected static class CacheKey
        implements Key
    {

        private final Plugin plugin;

        private final WorkspaceRepository workspace;

        private final LocalRepository localRepo;

        private final List<RemoteRepository> repositories;

        private final ClassLoader parentRealm;

        private final Map<String, ClassLoader> foreignImports;

        private final DependencyFilter filter;

        private final int hashCode;

        public CacheKey(Plugin plugin, ClassLoader parentRealm, Map<String, ClassLoader> foreignImports,
                        DependencyFilter dependencyFilter, List<RemoteRepository> repositories,
                        RepositorySystemSession session )
        {
            this.plugin = plugin.clone();
            this.workspace = RepositoryUtils.getWorkspace( session );
            this.localRepo = session.getLocalRepository();
            this.repositories = new ArrayList<>( repositories.size() );
            for ( RemoteRepository repository : repositories )
            {
                if ( repository.isRepositoryManager() )
                {
                    this.repositories.addAll( repository.getMirroredRepositories() );
                }
                else
                {
                    this.repositories.add( repository );
                }
            }
            this.parentRealm = parentRealm;
            this.foreignImports =
                ( foreignImports != null ) ? foreignImports : Collections.<String, ClassLoader>emptyMap();
            this.filter = dependencyFilter;

            int hash = 17;
            hash = hash * 31 + CliCacheUtils.pluginHashCode( plugin );
            hash = hash * 31 + Objects.hashCode( workspace );
            hash = hash * 31 + Objects.hashCode( localRepo );
            hash = hash * 31 + RepositoryUtils.repositoriesHashCode( repositories );
            hash = hash * 31 + Objects.hashCode( parentRealm );
            hash = hash * 31 + this.foreignImports.hashCode();
            hash = hash * 31 + Objects.hashCode( dependencyFilter );
            this.hashCode = hash;
        }

        @Override
        public String toString()
        {
            return plugin.getId();
        }

        @Override
        public int hashCode()
        {
            return hashCode;
        }

        @Override
        public boolean equals( Object o )
        {
            if ( o == this )
            {
                return true;
            }

            if ( !( o instanceof CacheKey ) )
            {
                return false;
            }

            CacheKey that = (CacheKey) o;

            return parentRealm == that.parentRealm
                && CliCacheUtils.pluginEquals( plugin, that.plugin )
                && Objects.equals( workspace, that.workspace )
                && Objects.equals( localRepo, that.localRepo )
                && RepositoryUtils.repositoriesEquals( this.repositories, that.repositories )
                && Objects.equals( filter, that.filter )
                && Objects.equals( foreignImports, that.foreignImports );
        }
    }

    protected static class ChecksumCacheRecord extends CacheRecord {

        static class PathChecksum {
            private static final int BUFFER_SIZE = 4096;

            static byte[] checksum(Path path, MessageDigest digest) {
                digest.reset();
                try (InputStream in = Files.newInputStream(path)) {
                    byte[] buf = new byte[BUFFER_SIZE];
                    int len;
                    while ((len = in.read(buf)) >= 0) {
                        digest.update(buf, 0, len);
                    }
                    return digest.digest();
                } catch (IOException e) {
                    throw new RuntimeException("Could not read "+ path, e);
                }
            }

            final Path path;
            final byte[] checksum;
            PathChecksum(Path path, byte[] checksum) {
                this.path = path;
                this.checksum = checksum;
            }
            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                PathChecksum that = (PathChecksum) o;
                return path.equals(that.path) && Arrays.equals(this.checksum, that.checksum);
            }
            @Override
            public int hashCode() {
                return Objects.hash(path, Arrays.hashCode(checksum));
            }
            public static MessageDigest newDigest() {
                try {
                    return MessageDigest.getInstance("MD5");
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        final List<PathChecksum> checksums;
        public ChecksumCacheRecord(ClassRealm realm, List<Artifact> artifacts) {
            super(realm, artifacts);
            final MessageDigest digest = PathChecksum.newDigest();
            this.checksums = getArtifacts().stream()
                    .map(Artifact::getFile)
                    .map(File::toPath)
                    .map(p -> new PathChecksum(p, PathChecksum.checksum(p, digest)))
                    .collect(Collectors.toList());
        }

        public boolean isValid() {
            final List<Artifact> artifacts = getArtifacts();
            if (this.checksums.size() != artifacts.size()) {
                return false;
            }
            final Iterator<Artifact> artifactIt = artifacts.iterator();
            final Iterator<PathChecksum> checksumIt = checksums.iterator();
            final MessageDigest digest = PathChecksum.newDigest();
            while (artifactIt.hasNext() && checksumIt.hasNext()) {
                final Path path = artifactIt.next().getFile().toPath();
                if (!Files.exists(path)) {
                    return false;
                }
                final PathChecksum checksum = checksumIt.next();
                if (!path.equals(checksum.path)) {
                    return false;
                }
                if (!Arrays.equals(PathChecksum.checksum(path, digest), checksum.checksum)) {
                    return false;
                }
            }
            return true;
        }

        public void dispose() {
            ClassRealm realm = getRealm();
            try
            {
                realm.getWorld().disposeRealm( realm.getId() );
            }
            catch ( NoSuchRealmException e )
            {
                // ignore
            }
        }
    }

    protected final Map<Key, ChecksumCacheRecord> cache = new ConcurrentHashMap<>();

    public Key createKey(Plugin plugin, ClassLoader parentRealm, Map<String, ClassLoader> foreignImports,
                         DependencyFilter dependencyFilter, List<RemoteRepository> repositories,
                         RepositorySystemSession session )
    {
        return new CacheKey( plugin, parentRealm, foreignImports, dependencyFilter, repositories, session );
    }

    public CacheRecord get( Key key )
    {
        ChecksumCacheRecord record = cache.get( key );
        if (record != null && !record.isValid()) {
            record.dispose();
            record = null;
            cache.remove( key );
        }
        return record;
    }

    public CacheRecord put( Key key, ClassRealm pluginRealm, List<Artifact> pluginArtifacts )
    {
        Objects.requireNonNull( pluginRealm, "pluginRealm cannot be null" );
        Objects.requireNonNull( pluginArtifacts, "pluginArtifacts cannot be null" );

        if ( cache.containsKey( key ) )
        {
            throw new IllegalStateException( "Duplicate plugin realm for plugin " + key );
        }

        ChecksumCacheRecord record = new ChecksumCacheRecord( pluginRealm, pluginArtifacts );

        cache.put( key, record );

        return record;
    }

    public void flush()
    {
        for ( ChecksumCacheRecord record : cache.values() )
        {
            record.dispose();
        }
        cache.clear();
    }



    protected static int pluginHashCode( Plugin plugin )
    {
        return CliCacheUtils.pluginHashCode( plugin );
    }

    protected static boolean pluginEquals( Plugin a, Plugin b )
    {
        return CliCacheUtils.pluginEquals( a, b );
    }

    public void register( MavenProject project, Key key, CacheRecord record )
    {
        // default cache does not track plugin usage
    }

    public void dispose()
    {
        flush();
    }

}
