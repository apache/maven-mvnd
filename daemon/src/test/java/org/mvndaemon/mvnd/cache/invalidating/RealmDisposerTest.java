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

import java.lang.ref.WeakReference;
import java.net.URL;

import com.google.inject.Injector;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.eclipse.sisu.bean.BeanManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class RealmDisposerTest {

    /** Loaded a second time by the child realm, like a mojo class of a plugin realm. */
    public static class Component {}

    @Test
    void disposedRealmIsNotRetainedByLifecycleCache() throws Exception {
        DefaultPlexusContainer container =
                new DefaultPlexusContainer(new DefaultContainerConfiguration().setJSR250Lifecycle(true));
        try {
            BeanManager beanManager = container.lookup(Injector.class).getInstance(BeanManager.class);
            RealmDisposer disposer = new RealmDisposer(beanManager);

            WeakReference<ClassRealm> realmRef = createManageAndDispose(container, beanManager, disposer);

            for (int i = 0; i < 50 && realmRef.get() != null; i++) {
                System.gc();
                Thread.sleep(20);
            }
            assertNull(realmRef.get(), "disposed realm should be garbage collectable");
        } finally {
            container.dispose();
        }
    }

    private static WeakReference<ClassRealm> createManageAndDispose(
            DefaultPlexusContainer container, BeanManager beanManager, RealmDisposer disposer) throws Exception {
        ClassRealm realm = container.createChildRealm("plugin>test:realm-disposer-test:1.0");
        URL testClasses = Component.class.getProtectionDomain().getCodeSource().getLocation();
        realm.addURL(testClasses);
        Class<?> componentClass = realm.loadClass(Component.class.getName());
        assertSame(realm, componentClass.getClassLoader());

        // what Sisu does for every component class it binds (PlexusLifecycleManager -> LifecycleManager)
        beanManager.manage(componentClass);

        disposer.dispose(realm);
        return new WeakReference<>(realm);
    }
}
