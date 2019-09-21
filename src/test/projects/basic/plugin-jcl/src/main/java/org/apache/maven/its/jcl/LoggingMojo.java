package org.apache.maven.its.jcl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "log")
public class LoggingMojo extends AbstractMojo {

  private final Log logger = LogFactory.getLog(getClass());

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    logger.info("jcl log message");
  }

}
