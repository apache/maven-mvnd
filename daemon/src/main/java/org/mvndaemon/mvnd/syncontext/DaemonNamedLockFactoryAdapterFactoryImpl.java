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
package org.mvndaemon.mvnd.syncontext;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Map;

import org.eclipse.aether.impl.RepositorySystemLifecycle;
import org.eclipse.aether.internal.impl.synccontext.named.NameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.NameMappers;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapterFactoryImpl;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.sisu.Priority;

/**
 * Mvnd named lock factory implementation: it differs from
 * {@link org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapterFactoryImpl} only by default values.
 */
@Singleton
@Named
@Priority(10)
public final class DaemonNamedLockFactoryAdapterFactoryImpl extends NamedLockFactoryAdapterFactoryImpl {
    @Inject
    public DaemonNamedLockFactoryAdapterFactoryImpl(
            final Map<String, NamedLockFactory> factories,
            final Map<String, NameMapper> nameMappers,
            final RepositorySystemLifecycle lifecycle) {
        super(factories, FileLockNamedLockFactory.NAME, nameMappers, NameMappers.FILE_GAV_NAME, lifecycle);
    }
}
