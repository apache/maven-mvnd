package org.jboss.fuse.mvnd.logging.internal;

import java.io.File;

import io.takari.maven.testing.TestProperties;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenInstallations;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(MavenJUnitTestRunner.class)
@MavenInstallations({"target/maven-distro"})
public class LoggingIT {

  @Rule
  public final TestResources resources = new TestResources();

  public final TestProperties properties = new TestProperties();

  public final MavenRuntime maven;

  public LoggingIT(MavenRuntimeBuilder verifierBuilder) throws Exception {
    this.maven = verifierBuilder //
        // .withExtension(new File("target/classes")) //
        .withCliOptions("-B").build();
  }

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("basic");
    maven.forProject(basedir) //
        .withCliOption("-Dmaven.logging=ci") //
        .execute("package") //
        .assertErrorFreeLog() //
        // slf4-bridges
        .assertLogText("I [test-project@main] slf4j-bridges jcl log message") //
        .assertLogText("I [test-project@main] slf4j-bridges jul log message") //
        .assertLogText("I [test-project@main] slf4j-bridges log4j 1.x log message") //
        .assertLogText("I [test-project@main] slf4j-bridges slf4j log message") //
        // sysout
        .assertLogText("I [test-project@main] System.out message") //
        .assertLogText("W [test-project@main] System.err message") //
        .assertLogText("W [test-project@main] java.lang.Exception") //
        //
        .assertLogText("I [test-project@main] slf4j log message") // straight slf4j
        .assertLogText("I [test-project@main] jul log message") // java.util.logging
        .assertLogText("I [test-project@main] log4j 1.x log message") // log4j 1.x
        .assertLogText("I [test-project@main] logback log message") // logback
        .assertLogText("I [test-project@main] jcl log message") // commons logging
    ;
  }
}
