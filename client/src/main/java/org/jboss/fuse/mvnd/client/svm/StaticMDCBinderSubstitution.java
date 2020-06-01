package org.jboss.fuse.mvnd.client.svm;

import org.slf4j.MDC;
import org.slf4j.impl.StaticMDCBinder;
import org.slf4j.spi.MDCAdapter;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(MDC.class)
final class StaticMDCBinderSubstitution {

    @Substitute
    private static MDCAdapter bwCompatibleGetMDCAdapterFromBinder() throws NoClassDefFoundError {
        return StaticMDCBinder.SINGLETON.getMDCA();
    }

}
