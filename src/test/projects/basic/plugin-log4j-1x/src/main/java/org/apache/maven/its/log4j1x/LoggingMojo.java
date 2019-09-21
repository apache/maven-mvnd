package org.apache.maven.its.log4j1x;

import org.apache.log4j.Logger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "log")
public class LoggingMojo extends AbstractMojo {

  private final Logger logger = Logger.getLogger(getClass());

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    logger.info("log4j 1.x log message");
  }

}
