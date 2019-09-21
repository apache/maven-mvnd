package org.apache.maven.its.logback;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

@Mojo(name = "log")
public class LoggingMojo extends AbstractMojo {

  private final Logger logger =
      ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(getClass());

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    logger.info("logback log message");
  }

}
