package org.apache.maven.its.sysout;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "log")
public class LoggingMojo extends AbstractMojo {

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    System.out.println("System.out message");
    System.err.println("System.err message");

    new Exception().printStackTrace();
  }

}
