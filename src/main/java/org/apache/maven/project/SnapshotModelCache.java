package org.apache.maven.project;

import org.apache.maven.model.building.ModelCache;

public class SnapshotModelCache implements ModelCache {

    private final ModelCache globalCache;
    private final ModelCache reactorCache;

    public SnapshotModelCache(ModelCache globalCache) {
        this.globalCache = globalCache;
        this.reactorCache = new ReactorModelCache();
    }

    @Override
    public void put(String groupId, String artifactId, String version, String tag, Object data) {
        getDelegate(version).put(groupId, artifactId, version, tag, data);
    }

    @Override
    public Object get(String groupId, String artifactId, String version, String tag) {
        return getDelegate(version).get(groupId, artifactId, version, tag);
    }

    private ModelCache getDelegate(String version) {
        return version.contains("SNAPSHOT") ? reactorCache : globalCache;
    }
}
