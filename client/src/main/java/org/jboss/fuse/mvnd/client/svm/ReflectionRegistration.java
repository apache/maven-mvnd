package org.jboss.fuse.mvnd.client.svm;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;

@AutomaticFeature
public class ReflectionRegistration implements Feature {
    public void beforeAnalysis(BeforeAnalysisAccess access) {
//        try {
//        } catch (NoSuchMethodException | SecurityException e) {
//            throw new RuntimeException(e);
//        }
    }
}
