package org.jboss.fuse.mvnd.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.jboss.fuse.mvnd.client.Client;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MvndTestExtension.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MvndNativeTest {

    /**
     * The path to the root directory of a test project relative to the current maven module directory. E.g.
     * <code>@MvndTest(projectDir = "src/test/projects/my-project")</code>
     */
    String projectDir();

    /**
     * Timeout for {@link Client#execute(org.jboss.fuse.mvnd.client.ClientOutput, java.util.List)} in seconds
     */
    long timeoutSec() default 30;
}
