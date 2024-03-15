= `mvnd` - the Maven Daemon
:toc: macro

image::https://img.shields.io/twitter/url/https/twitter.com/mvndaemon.svg?style=social&label=Follow%20%40mvndaemon[link="https://twitter.com/mvndaemon"]

toc::[]

== Introduction

This project aims at providing faster https://maven.apache.org/[Maven] builds using techniques known from Gradle and
Takari.

Architecture overview:

* `mvnd` embeds Maven (so there is no need to install Maven separately).
* The actual builds happen inside a long living background process, a.k.a. daemon.
* One daemon instance can serve multiple consecutive requests from the `mvnd` client.
* The `mvnd` client is a native executable built using https://www.graalvm.org/reference-manual/native-image/[GraalVM].
  It starts faster and uses less memory compared to starting a traditional JVM.
* Multiple daemons can be spawned in parallel if there is no idle daemon to serve a build request.

This architecture brings the following advantages:

* The JVM for running the actual builds does not need to get started anew for each build.
* The classloaders holding classes of Maven plugins are cached over multiple builds. The plugin jars are thus read
  and parsed just once. SNAPSHOT versions of Maven plugins are not cached.
* The native code produced by the Just-In-Time (JIT) compiler inside the JVM is kept too. Compared to stock Maven,
  less time is spent by the JIT compilation. During the repeated builds the JIT-optimized code is available
  immediately. This applies not only to the code coming from Maven plugins and Maven Core, but also to all code coming
  from the JDK itself.

== Additional features

`mvnd` brings the following features on top of the stock Maven:

* By default, `mvnd` is building your modules in parallel using multiple CPU cores. The number of utilized cores is
  given by the formula `Math.max(Runtime.getRuntime().availableProcessors() - 1, 1)`. If your source tree does not
  support parallel builds, pass `-T1` into the command line to make your build serial.
* Improved console output: we believe that the output of a parallel build on stock Maven is hard to follow. Therefore,
we implemented a simplified non-rolling view showing the status of each build thread on a separate line. This is
what it looks like on a machine with 24 cores:
+
image::https://user-images.githubusercontent.com/1826249/103917178-94ee4500-510d-11eb-9abb-f52dae58a544.gif[]
+
Once the build is finished, the complete Maven output is forwarded to the console.

== How to install `mvnd`

=== Install using https://sdkman.io/[SDKMAN!]

If SDKMAN! supports your operating system, it is as easy as

[source,shell]
----
$ sdk install mvnd
----

If you used the manual install in the past, please make sure that the settings in `~/.m2/mvnd.properties` still make
sense. With SDKMAN!, the `~/.m2/mvnd.properties` file is typically not needed at all, because both `JAVA_HOME` and
`MVND_HOME` are managed by SDKMAN!.

=== Install using https://brew.sh/[Homebrew]

[source,shell]
----
$ brew install mvndaemon/homebrew-mvnd/mvnd
----

=== Other installers

We're looking for contribution to support https://www.macports.org[MacPorts],
https://community.chocolatey.org/packages/mvndaemon/[Chocolatey], https://scoop.sh/[Scoop] or
https://github.com/joschi/asdf-mvnd#install[asdf].  If you fancy helping us...

////
=== Install using https://www.macports.org[MacPorts]

[source,shell]
----
$ sudo port install mvnd
----

=== Install using https://community.chocolatey.org/packages/mvndaemon/[Chocolatey]

[source,shell]
----
$ choco install mvndaemon
----

=== Install using https://scoop.sh/[Scoop]

[source,shell]
----
$ scoop install mvndaemon
----

=== Install using https://github.com/joschi/asdf-mvnd#install[asdf]

[source,shell]
----
$ asdf plugin-add mvnd
$ asdf install mvnd latest
----
////

=== Set up completion

Optionally, you can set up completion as follows:
[source,shell]
----
# ensure that MVND_HOME points to your mvnd distribution, note that sdkman does it for you
$ echo 'source $MVND_HOME/bin/mvnd-bash-completion.bash' >> ~/.bashrc
----
`bash` is the only shell supported at this time.

=== Note for oh-my-zsh users ===

Users that use `oh-my-zsh` often use completion for maven.  The default maven completion plugin defines `mvnd` as an alias to `mvn deploy`. So before being able to use `mvnd`, you need to unalias using the following command:
[source,shell]
----
$ unalias mvnd
----


=== Install manually

* Download the latest ZIP suitable for your platform from https://downloads.apache.org/maven/mvnd/
* Unzip to a directory of your choice
* Add the `bin` directory to `PATH`
* Optionally, you can create `~/.m2/mvnd.properties` and set the `java.home` property in case you do not want to bother
  with setting the `JAVA_HOME` environment variable.
* Test whether `mvnd` works:
+
[source,shell]
----
$ mvnd --version
Maven Daemon 0.0.11-linux-amd64 (native)
Terminal: org.jline.terminal.impl.PosixSysTerminal with pty org.jline.terminal.impl.jansi.osx.OsXNativePty
Apache Maven 3.6.3 (cecedd343002696d0abb50b32b541b8a6ba2883f)
Maven home: /home/ppalaga/orgs/mvnd/mvnd/daemon/target/maven-distro
Java version: 11.0.1, vendor: AdoptOpenJDK, runtime: /home/data/jvm/adopt-openjdk/jdk-11.0.1+13
Default locale: en_IE, platform encoding: UTF-8
OS name: "linux", version: "5.6.13-200.fc31.x86_64", arch: "amd64", family: "unix"
----
+
If you are on Windows and see a message that `VCRUNTIME140.dll was not found`, you need to install
`vc_redist.x64.exe` from https://support.microsoft.com/en-us/help/2977003/the-latest-supported-visual-c-downloads.
See https://github.com/oracle/graal/issues/1762 for more information.
+
If you are on macOS, you'll need to remove the quarantine flags from all the files after unpacking the archive:
[source,shell]
----
$ xattr -r -d com.apple.quarantine mvnd-x.y.z-darwin-amd64
----

== Usage

`mvnd` is designed to accept the same command line options like stock `mvn` (plus some extras - see below), e.g.:

[source,shell]
----
mvnd verify
----

== `mvnd` specific options

`--status` lists running daemons

`--stop` kills all running daemons

`mvnd --help` prints the complete list of options


== Configuration
Configuration can be provided through the properties file.  Mvnd reads the properties file from the following locations:

* the properties path supplied using `MVND_PROPERTIES_PATH` environment variable or `mvnd.propertiesPath` system variable
* the local properties path located at `[PROJECT_HOME]/.mvn/mvnd.properties`
* the user properties path located at: `[USER_HOME]/.m2/mvnd.properties`
* the system properties path located at: `[MVND_HOME]/conf/mvnd.properties`

Properties defined in the first files will take precedence over properties specified in a lower ranked file.

A few special properties do not follow the above mechanism:

* `mvnd.daemonStorage`: this property defines the location where mvnd stores its files (registry and daemon logs).  This property can only be defined as a system property on the command line
* `mvnd.id`: this property is used internally to identify the daemon being created
* `mvnd.extClasspath`: internal option to specify the maven extension classpath
* `mvnd.coreExtensions`: internal option to specify the list of maven extension to register

For a full list of available properties please see 
https://github.com/apache/maven-mvnd/blob/master/dist/src/main/distro/conf/mvnd.properties[/dist/src/main/distro/conf/mvnd.properties].

== Build `mvnd` from source

=== Prerequisites:

* `git`
* Maven
* Download and unpack GraalVM CE from https://github.com/graalvm/graalvm-ce-builds/releases[GitHub]
* Set `JAVA_HOME` to where you unpacked GraalVM in the previous step. Check that `java -version` output is as
  expected:
+
[source,shell]
----
$ $JAVA_HOME/bin/java -version
openjdk version "11.0.9" 2020-10-20
OpenJDK Runtime Environment GraalVM CE 20.3.0 (build 11.0.9+10-jvmci-20.3-b06)
OpenJDK 64-Bit Server VM GraalVM CE 20.3.0 (build 11.0.9+10-jvmci-20.3-b06, mixed mode, sharing)
----
+
* Install the `native-image` tool:
+
[source,shell]
----
$ $JAVA_HOME/bin/gu install native-image
----

* `native-image` may require additional software to be installed depending on your platform - see the
https://www.graalvm.org/reference-manual/native-image/#prerequisites[`native-image` documentation].

=== Build `mvnd`

[source,shell]
----
$ git clone https://github.com/apache/maven-mvnd.git
$ cd maven-mvnd
$ mvn clean verify -Pnative
...
$ cd client
$ file target/mvnd
target/mvnd: ELF 64-bit LSB executable, x86-64, version 1 (SYSV), dynamically linked, interpreter /lib64/ld-linux-x86-64.so.2, BuildID[sha1]=93a554f3807550a13c986d2af9a311ef299bdc5a, for GNU/Linux 3.2.0, with debug_info, not stripped
$ ls -lh target/mvnd
-rwxrwxr-x. 1 ppalaga ppalaga 25M Jun  2 13:23 target/mvnd
----

Please note that if you are using Windows as your operating system you will need the following prerequisites for building `maven-mvnd`:
a version of Visual Studio with the workload "Desktop development with C++" and the individual component "Windows Universal CRT SDK".

=== Install `mvnd`

[source, shell]
----
$ cp -R dist/target/mvnd-[version] [target-dir]
----

Then you can simply add `[target-dir]/bin` to your `PATH` and run `mvnd`. 

We're happy to improve `mvnd`, so https://github.com/apache/maven-mvnd/issues[feedback] is most welcome!
