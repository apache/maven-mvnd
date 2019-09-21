package org.apache.maven.its.slf4j;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mojo(name = "log")
public class LoggingMojo extends AbstractMojo {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    logger.info("slf4j log message");
  }

}
