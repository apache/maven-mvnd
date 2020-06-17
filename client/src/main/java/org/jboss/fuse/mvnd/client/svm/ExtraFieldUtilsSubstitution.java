package org.jboss.fuse.mvnd.client.svm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipException;

import org.apache.commons.compress.archivers.zip.AsiExtraField;
import org.apache.commons.compress.archivers.zip.ExtraFieldParsingBehavior;
import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ExtraFieldUtils.UnparseableExtraField;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipShort;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(ExtraFieldUtils.class)
@Substitute
public final class ExtraFieldUtilsSubstitution {

    @Alias
    @RecomputeFieldValue(kind = Kind.None)
    private static final Map<ZipShort, Class<?>> implementations;

    static {
        implementations = new ConcurrentHashMap<>();
        registerInst(new AsiExtraField());
    }


    public static void registerInst(ZipExtraField ze) {
        implementations.put(ze.getHeaderId(), ze.getClass());
    }

    @KeepOriginal
    public static ZipExtraField createExtraField(final ZipShort headerId)
        throws InstantiationException, IllegalAccessException {
        return null;
    }

    @KeepOriginal
    public static ZipExtraField createExtraFieldNoDefault(final ZipShort headerId)
        throws InstantiationException, IllegalAccessException {
        return null;
    }

    @KeepOriginal
    public static ZipExtraField[] parse(final byte[] data) throws ZipException {
        return null;
    }

    @KeepOriginal
    public static ZipExtraField[] parse(final byte[] data, final boolean local)
        throws ZipException {
        return null;
    }

    @KeepOriginal
    public static ZipExtraField[] parse(final byte[] data, final boolean local,
                                        final UnparseableExtraField onUnparseableData)
        throws ZipException {
        return null;
    }

    @KeepOriginal
    public static ZipExtraField[] parse(final byte[] data, final boolean local,
                                        final ExtraFieldParsingBehavior parsingBehavior)
        throws ZipException {
        return null;
    }

    @KeepOriginal
    public static byte[] mergeLocalFileDataData(final ZipExtraField[] data) {
        return null;
    }

    @KeepOriginal
    public static byte[] mergeCentralDirectoryData(final ZipExtraField[] data) {
        return null;
    }

    @KeepOriginal
    public static ZipExtraField fillExtraField(final ZipExtraField ze, final byte[] data, final int off,
        final int len, final boolean local) throws ZipException {
        return null;
    }

}
