package org.apache.maven.its.jul;

import java.util.logging.Logger;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "log")
public class LoggingMojo extends AbstractMojo {

  private final Logger logger = Logger.getLogger(getClass().getName());

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    logger.info("jul log message");
  }

}
