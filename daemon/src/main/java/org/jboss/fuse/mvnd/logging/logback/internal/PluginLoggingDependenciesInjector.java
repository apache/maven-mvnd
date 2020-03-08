/*
 * Copyright (c) 2015-2016 salesforce.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.fuse.mvnd.logging.logback.internal;


import java.util.ListIterator;

import javax.inject.Named;

import org.apache.maven.classrealm.ClassRealmConstituent;
import org.apache.maven.classrealm.ClassRealmManagerDelegate;
import org.apache.maven.classrealm.ClassRealmRequest;
import org.codehaus.plexus.classworlds.realm.ClassRealm;

/**
 * Replaces plugin logging dependencies with corresponding libraries used by the core.
 */
@Named
public class PluginLoggingDependenciesInjector implements ClassRealmManagerDelegate {

    @Override
    public void setupRealm(ClassRealm classRealm, ClassRealmRequest request) {
        final ListIterator<ClassRealmConstituent> iter = request.getConstituents().listIterator();
        while (iter.hasNext()) {
            final ClassRealmConstituent entry = iter.next();
            // logback
            if ("ch.qos.logback".equals(entry.getGroupId())
                    && "logback-core".equals(entry.getArtifactId())) {
                iter.remove();
                request.getForeignImports().put("ch.qos.logback", getClass().getClassLoader());
            } else if ("ch.qos.logback".equals(entry.getGroupId())
                    && "logback-classic".equals(entry.getArtifactId())) {
                iter.remove();
                request.getForeignImports().put("ch.qos.logback", getClass().getClassLoader());
            }
            // jul->slf4j bridge
            else if ("org.slf4j".equals(entry.getGroupId())
                    && "jul-to-slf4j".equals(entry.getArtifactId())) {
                iter.remove();
            }
            // slf4j->bridge bridge
            else if ("org.slf4j".equals(entry.getGroupId())
                    && "slf4j-jdk14".equals(entry.getArtifactId())) {
                iter.remove();
            }
            // slf4j-nop bridge
            else if ("org.slf4j".equals(entry.getGroupId())
                    && "slf4j-nop".equals(entry.getArtifactId())) {
                iter.remove();
            }
            // slf4j-simple bridge
            else if ("org.slf4j".equals(entry.getGroupId())
                    && "slf4j-simple".equals(entry.getArtifactId())) {
                iter.remove();
            }
            // log4j 1.x
            else if (isLog4j1x(entry)) {
                iter.remove();
                request.getForeignImports().put("org.apache.log4j", getClass().getClassLoader());
            }
            // commons-logging
            else if (isCommonsLogging(entry)) {
                iter.remove();
                request.getForeignImports().put("org.apache.commons.logging", getClass().getClassLoader());
            }
        }
    }

    private boolean isLog4j1x(ClassRealmConstituent entry) {
        if ("log4j".equals(entry.getGroupId()) && "log4j".equals(entry.getArtifactId())) {
            return true;
        }
        if ("org.slf4j".equals(entry.getGroupId())
                && "log4j-over-slf4j".equals(entry.getArtifactId())) {
            return true;
        }
        return false;
    }

    private boolean isCommonsLogging(ClassRealmConstituent entry) {
        if ("commons-logging".equals(entry.getGroupId())
                && "commons-logging".equals(entry.getArtifactId())) {
            return true;
        }
        if ("org.slf4j".equals(entry.getGroupId()) && "jcl-over-slf4j".equals(entry.getArtifactId())) {
            return true;
        }
        return false;
    }

}
