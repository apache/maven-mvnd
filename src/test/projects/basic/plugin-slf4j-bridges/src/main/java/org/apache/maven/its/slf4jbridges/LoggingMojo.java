package org.apache.maven.its.slf4jbridges;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "log")
public class LoggingMojo extends AbstractMojo {

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    org.apache.commons.logging.LogFactory.getLog(getClass()).info("slf4j-bridges jcl log message");
    java.util.logging.Logger.getLogger(getClass().getName()).info("slf4j-bridges jul log message");
    org.apache.log4j.Logger.getLogger(getClass()).info("slf4j-bridges log4j 1.x log message");
    org.slf4j.LoggerFactory.getLogger(getClass()).info("slf4j-bridges slf4j log message");
  }

}
