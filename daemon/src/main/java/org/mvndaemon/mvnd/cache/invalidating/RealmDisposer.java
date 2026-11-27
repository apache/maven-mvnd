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
package org.mvndaemon.mvnd.cache.invalidating;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.lang.reflect.Field;

import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.eclipse.sisu.bean.BeanManager;
import org.eclipse.sisu.bean.LifecycleManager;
import org.eclipse.sisu.plexus.PlexusLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Disposes the class realms evicted from the mvnd caches.
 * <p>
 * Besides disposing the realm in its {@code ClassWorld}, this also removes the classes of the realm from the JSR250
 * lifecycle cache of Sisu ({@link LifecycleManager}). That cache uses strong {@code Class} keys and Sisu does not flush
 * it when a realm is disposed (eclipse-sisu/sisu-project#281), so every evicted realm would otherwise stay reachable
 * for the lifetime of the daemon. This can be removed once mvnd uses a Sisu version that flushes the cache itself.
 */
@Named
@Singleton
public class RealmDisposer {

    private static final Logger LOG = LoggerFactory.getLogger(RealmDisposer.class);

    private final LifecycleManager lifecycleManager;

    @Inject
    public RealmDisposer(BeanManager beanManager) {
        this.lifecycleManager = findLifecycleManager(beanManager);
    }

    public void dispose(ClassRealm realm) {
        try {
            realm.getWorld().disposeRealm(realm.getId());
        } catch (NoSuchRealmException e) {
            // ignore
        }
        if (lifecycleManager != null) {
            lifecycleManager.flushCacheFor(clazz -> clazz.getClassLoader() == realm);
        }
    }

    private static LifecycleManager findLifecycleManager(BeanManager beanManager) {
        if (beanManager instanceof LifecycleManager) {
            return (LifecycleManager) beanManager;
        }
        if (beanManager instanceof PlexusLifecycleManager) {
            try {
                Field delegate = PlexusLifecycleManager.class.getDeclaredField("delegate");
                delegate.setAccessible(true);
                Object jsr250Lifecycle = delegate.get(beanManager);
                if (jsr250Lifecycle instanceof LifecycleManager) {
                    return (LifecycleManager) jsr250Lifecycle;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.debug("Could not access the JSR250 lifecycle manager of Sisu", e);
            }
        }
        return null;
    }
}
