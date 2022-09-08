# Changelog

## [0.8.1](https://github.com/apache/maven-mvnd/tree/0.8.1) (2022-09-08)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.8.0...0.8.1)

**Implemented enhancements:**

- Use interpolation when loading properties, fixes \#676 [\#677](https://github.com/apache/maven-mvnd/pull/677) ([gnodet](https://github.com/gnodet))

**Fixed bugs:**

- bash-completion gives error "\_\_git\_reassemble\_comp\_words\_by\_ref: command not found" [\#670](https://github.com/apache/maven-mvnd/issues/670)
- Daemon suddenly stopped working - BufferUnderflowException [\#645](https://github.com/apache/maven-mvnd/issues/645)
- Fix plugins' parent classloader to not include libraries, fixes \#681 [\#683](https://github.com/apache/maven-mvnd/pull/683) ([gnodet](https://github.com/gnodet))
- Allow to the registry to be resized to avoid registry corruption \(\#645\) [\#646](https://github.com/apache/maven-mvnd/pull/646) ([gnodet](https://github.com/gnodet))

**Closed issues:**

- ClassNotFoundException when starting jetty with jetty-maven-plugin [\#681](https://github.com/apache/maven-mvnd/issues/681)
- Use interpolation when loading properties [\#676](https://github.com/apache/maven-mvnd/issues/676)
- How do I change the JDK dynamically [\#675](https://github.com/apache/maven-mvnd/issues/675)
- Ability to configure the daemon storage location [\#673](https://github.com/apache/maven-mvnd/issues/673)
- Deployed timestamped snapshot version does not change [\#672](https://github.com/apache/maven-mvnd/issues/672)
- Logs go to stdout breaking scripts [\#671](https://github.com/apache/maven-mvnd/issues/671)
- release .tar.gz format precompiled binary archives  [\#668](https://github.com/apache/maven-mvnd/issues/668)
- Exception in thread "main" java.io.UncheckedIOException: java.nio.charset.MalformedInputException: Input length = 1 [\#667](https://github.com/apache/maven-mvnd/issues/667)
- Hashes missing from latest release \(0.8.0\) [\#666](https://github.com/apache/maven-mvnd/issues/666)
- Improved support for IntelliJ Idea [\#664](https://github.com/apache/maven-mvnd/issues/664)
- Missing argument for option -D [\#662](https://github.com/apache/maven-mvnd/issues/662)
- Support Maven 3.8.6 [\#660](https://github.com/apache/maven-mvnd/issues/660)
- Cannot suppress debug logging as of 0.8.0 on Homebrew on M1 Mac \(previous versions didn't have this issue\) [\#656](https://github.com/apache/maven-mvnd/issues/656)
- mvnd goal execution id display inconsistent with Maven [\#653](https://github.com/apache/maven-mvnd/issues/653)
- Add scoop installation to readme [\#640](https://github.com/apache/maven-mvnd/issues/640)

**Merged pull requests:**

- Improve Intellij integration, fixes \#664 [\#684](https://github.com/apache/maven-mvnd/pull/684) ([gnodet](https://github.com/gnodet))
- Add missing function for mvnd-bash-completion, fixes \#670 [\#682](https://github.com/apache/maven-mvnd/pull/682) ([gnodet](https://github.com/gnodet))
- System properties should have precedence over environment variables, fixes \#675 [\#680](https://github.com/apache/maven-mvnd/pull/680) ([gnodet](https://github.com/gnodet))
- Missing argument for option -D, fixes \#662 [\#679](https://github.com/apache/maven-mvnd/pull/679) ([gnodet](https://github.com/gnodet))
- Ability to configure the daemon storage location, fixes \#673 [\#678](https://github.com/apache/maven-mvnd/pull/678) ([gnodet](https://github.com/gnodet))
- Update to Maven 3.8.6 \#660 [\#661](https://github.com/apache/maven-mvnd/pull/661) ([robertk3s](https://github.com/robertk3s))
- Revert "Remove unused logback-client.xml file", fixes \#656 [\#658](https://github.com/apache/maven-mvnd/pull/658) ([gnodet](https://github.com/gnodet))
- Make mvnd coloring more consistent with maven, fixes \#653 [\#654](https://github.com/apache/maven-mvnd/pull/654) ([gnodet](https://github.com/gnodet))
- Add asdf install method [\#652](https://github.com/apache/maven-mvnd/pull/652) ([mattnelson](https://github.com/mattnelson))
- Cleanup [\#650](https://github.com/apache/maven-mvnd/pull/650) ([gnodet](https://github.com/gnodet))
- Update release scripts [\#648](https://github.com/apache/maven-mvnd/pull/648) ([gnodet](https://github.com/gnodet))
- Add scoop.sh to install instructions [\#647](https://github.com/apache/maven-mvnd/pull/647) ([bonepl](https://github.com/bonepl))
- Configure execution bit for required scripts in source distribution [\#643](https://github.com/apache/maven-mvnd/pull/643) ([hboutemy](https://github.com/hboutemy))
- Update RELEASING.adoc [\#641](https://github.com/apache/maven-mvnd/pull/641) ([delanym](https://github.com/delanym))

## [0.8.0](https://github.com/apache/maven-mvnd/tree/0.8.0) (2022-05-04)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.7.1...0.8.0)

**Closed issues:**

- building libmvndnative.\* creates root files in target directory [\#627](https://github.com/apache/maven-mvnd/issues/627)
- \[bug\] The first exec mvnd clean install is failed every time [\#613](https://github.com/apache/maven-mvnd/issues/613)
- clean fails cause of locked files [\#611](https://github.com/apache/maven-mvnd/issues/611)
- mvnd on Windows throws `java.lang.NumberFormatException: For input string: "self"` [\#608](https://github.com/apache/maven-mvnd/issues/608)
- `mvnd.exe` gives error about DLL [\#607](https://github.com/apache/maven-mvnd/issues/607)
- Log purging goes to stdout breaking scripts [\#604](https://github.com/apache/maven-mvnd/issues/604)
- NPE [\#597](https://github.com/apache/maven-mvnd/issues/597)
- Avoid caching parent with a version containing a property [\#594](https://github.com/apache/maven-mvnd/issues/594)
- How to integrate mvnd in jenkins? [\#592](https://github.com/apache/maven-mvnd/issues/592)
- When MVND is used to compile a project, the addClasspath parameter in the manifest of the configuration item compiled by maven-jar-plugin is invalid, and the class-path parameter is missing in the manifest. MF file in the compiled JAR package [\#590](https://github.com/apache/maven-mvnd/issues/590)
- docker jenkins mvnd configured build error！ [\#589](https://github.com/apache/maven-mvnd/issues/589)
- Local settings.xml results current folder used as repository [\#588](https://github.com/apache/maven-mvnd/issues/588)
- will there be an official docker image on docker hub [\#587](https://github.com/apache/maven-mvnd/issues/587)
- No message received within 3000ms,  [\#584](https://github.com/apache/maven-mvnd/issues/584)
- Speed comparison between mvn and mvnd [\#575](https://github.com/apache/maven-mvnd/issues/575)
- not fast [\#570](https://github.com/apache/maven-mvnd/issues/570)
- I failed to execute 'mvnd -version' on my MAC [\#569](https://github.com/apache/maven-mvnd/issues/569)
- I failed to execute MVND on MAC [\#568](https://github.com/apache/maven-mvnd/issues/568)
- Cannot change Platform Encoding [\#567](https://github.com/apache/maven-mvnd/issues/567)
- Let JVM set max heap size instead of a default value of 2GB \(`mvnd.maxHeapSize`\) [\#560](https://github.com/apache/maven-mvnd/issues/560)
- mvnd doesn't work with duplicate-finder-maven-plugin [\#559](https://github.com/apache/maven-mvnd/issues/559)
- build failed in some projects [\#558](https://github.com/apache/maven-mvnd/issues/558)
- macOS  Monterey doesn't work with mvnd [\#554](https://github.com/apache/maven-mvnd/issues/554)
- The ${MVND\_HOME}/mvn/conf/setting.xml is not used [\#553](https://github.com/apache/maven-mvnd/issues/553)
- How to solve version declaration setting in pom.xml [\#551](https://github.com/apache/maven-mvnd/issues/551)
- mvnd with lombok error [\#550](https://github.com/apache/maven-mvnd/issues/550)
- How do i set other maven repository？ [\#549](https://github.com/apache/maven-mvnd/issues/549)
- java 8 can't use [\#547](https://github.com/apache/maven-mvnd/issues/547)
- mvnd maven plugin [\#546](https://github.com/apache/maven-mvnd/issues/546)
- git bash show “bash: mvnd: command not found” on win 10  [\#545](https://github.com/apache/maven-mvnd/issues/545)
- jdk 1.8 exception [\#544](https://github.com/apache/maven-mvnd/issues/544)
- Refactor build & release workflow [\#542](https://github.com/apache/maven-mvnd/issues/542)
- Does not support the spring-boot-maven-plugin [\#537](https://github.com/apache/maven-mvnd/issues/537)
- mvn clean slow on Windows - alternative implementation [\#536](https://github.com/apache/maven-mvnd/issues/536)
- Improve the doc text of `mvnd.pluginRealmEvictPattern` option [\#533](https://github.com/apache/maven-mvnd/issues/533)
- Investigate the setEnv logic across JDK / OS [\#528](https://github.com/apache/maven-mvnd/issues/528)
- \[discuss\]`maven.version` conflict in pom.xml & system property [\#516](https://github.com/apache/maven-mvnd/issues/516)
- Second "clean" build in fails on Windows due to locked files [\#115](https://github.com/apache/maven-mvnd/issues/115)
- make libmvndnative.\* builds reproducible [\#628](https://github.com/apache/maven-mvnd/issues/628)
-  Update to latest maven 3.8.5? [\#615](https://github.com/apache/maven-mvnd/issues/615)
- java.nio.BufferUnderflowException occurs when I run any mvnd command [\#601](https://github.com/apache/maven-mvnd/issues/601)
- `java.lang.NoSuchMethodError: org.apache.maven.project.MavenProject.setArtifacts(Ljava/util/Set;)V` [\#579](https://github.com/apache/maven-mvnd/issues/579)
- Daemon reuse ignores differences in `.mvn/jvm.config` [\#576](https://github.com/apache/maven-mvnd/issues/576)
- NoSuchFileException when using the compile phase with reactor dependencies with classifiers [\#564](https://github.com/apache/maven-mvnd/issues/564)
- create Apache source-release distribution archive [\#543](https://github.com/apache/maven-mvnd/issues/543)
- Build hang and CPU skyrocket with sisu-index:indexMojo [\#527](https://github.com/apache/maven-mvnd/issues/527)
- Parallel build fails with NoSuchFileException for target/classes [\#500](https://github.com/apache/maven-mvnd/issues/500)

**Merged pull requests:**

- Use the easier --user option of docker [\#639](https://github.com/apache/maven-mvnd/pull/639) ([gnodet](https://github.com/gnodet))
- Use maven-mvnd as a distribution name [\#638](https://github.com/apache/maven-mvnd/pull/638) ([gnodet](https://github.com/gnodet))
- Use sisu apt processor [\#636](https://github.com/apache/maven-mvnd/pull/636) ([gnodet](https://github.com/gnodet))
- Drop Maven dupe classes [\#633](https://github.com/apache/maven-mvnd/pull/633) ([cstamas](https://github.com/cstamas))
- FIx files generated with wrong user id, fixes \#627 [\#632](https://github.com/apache/maven-mvnd/pull/632) ([gnodet](https://github.com/gnodet))
- Reproducible build for the native library on windows [\#631](https://github.com/apache/maven-mvnd/pull/631) ([gnodet](https://github.com/gnodet))
- Move BuildProperties to the client [\#630](https://github.com/apache/maven-mvnd/pull/630) ([gnodet](https://github.com/gnodet))
- don't skip build/ in source archive [\#629](https://github.com/apache/maven-mvnd/pull/629) ([hboutemy](https://github.com/hboutemy))
- prepare Reproducible Builds [\#626](https://github.com/apache/maven-mvnd/pull/626) ([hboutemy](https://github.com/hboutemy))
- only publish sha256, skip md5+sha1+sha512 [\#625](https://github.com/apache/maven-mvnd/pull/625) ([hboutemy](https://github.com/hboutemy))
- Replace `github.com/mvndaemon/mvnd` references [\#622](https://github.com/apache/maven-mvnd/pull/622) ([Stephan202](https://github.com/Stephan202))
- Upgrade to auto changelog 1.2 [\#621](https://github.com/apache/maven-mvnd/pull/621) ([gnodet](https://github.com/gnodet))
- Reapply Refactor build and release workflows apache\#574 [\#620](https://github.com/apache/maven-mvnd/pull/620) ([gnodet](https://github.com/gnodet))
- Fix typos in readme [\#618](https://github.com/apache/maven-mvnd/pull/618) ([Bananeweizen](https://github.com/Bananeweizen))
- Adding note on removing quarantine flag on macOS [\#599](https://github.com/apache/maven-mvnd/pull/599) ([gunnarmorling](https://github.com/gunnarmorling))
- Refactor build and release workflows [\#574](https://github.com/apache/maven-mvnd/pull/574) ([aalmiray](https://github.com/aalmiray))
- Reproducer for \#564 [\#565](https://github.com/apache/maven-mvnd/pull/565) ([gnodet](https://github.com/gnodet))
- introduce dependabot [\#563](https://github.com/apache/maven-mvnd/pull/563) ([lkwg82](https://github.com/lkwg82))
- Fixes url for GraalVM's native-image prerequisites [\#556](https://github.com/apache/maven-mvnd/pull/556) ([kornelrabczak](https://github.com/kornelrabczak))
- Improve setEnv logic, fixes \#528 [\#535](https://github.com/apache/maven-mvnd/pull/535) ([gnodet](https://github.com/gnodet))
- Improve the doc text of mvnd.pluginRealmEvictPattern option, fixes \#533 [\#534](https://github.com/apache/maven-mvnd/pull/534) ([gnodet](https://github.com/gnodet))
- Use err stream when purging logs during a build, fixes \#604 [\#617](https://github.com/apache/maven-mvnd/pull/617) ([gnodet](https://github.com/gnodet))
- Maven385 [\#616](https://github.com/apache/maven-mvnd/pull/616) ([gnodet](https://github.com/gnodet))
- Handle BufferUnderflowException as a possible registry corruption [\#614](https://github.com/apache/maven-mvnd/pull/614) ([gnodet](https://github.com/gnodet))
- Make `DaemonRegistry.getProcessId0` more robust [\#612](https://github.com/apache/maven-mvnd/pull/612) ([jglick](https://github.com/jglick))
- Remove default values for heap options [\#610](https://github.com/apache/maven-mvnd/pull/610) ([gnodet](https://github.com/gnodet))
- Fix mvn/bin/mvn debug output [\#606](https://github.com/apache/maven-mvnd/pull/606) ([gnodet](https://github.com/gnodet))
- Add missing mvnDebug scripts [\#605](https://github.com/apache/maven-mvnd/pull/605) ([gnodet](https://github.com/gnodet))
- Remove unused logback-client.xml file [\#603](https://github.com/apache/maven-mvnd/pull/603) ([gnodet](https://github.com/gnodet))
- Avoid caching parent with a version containing a property, fixes \#594 [\#602](https://github.com/apache/maven-mvnd/pull/602) ([gnodet](https://github.com/gnodet))
- Bump xstream from 1.4.18 to 1.4.19 [\#598](https://github.com/apache/maven-mvnd/pull/598) ([dependabot[bot]](https://github.com/apps/dependabot))
- Upgrade SLF4J to version 1.7.35 [\#591](https://github.com/apache/maven-mvnd/pull/591) ([oscerd](https://github.com/oscerd))
- Fix user's pronoun in mvnd.properties comment [\#585](https://github.com/apache/maven-mvnd/pull/585) ([findepi](https://github.com/findepi))
- Remove unused subclass [\#582](https://github.com/apache/maven-mvnd/pull/582) ([gnodet](https://github.com/gnodet))
- Daemon reuse ignores differences in `.mvn/jvm.config`, fixes \#576 [\#580](https://github.com/apache/maven-mvnd/pull/580) ([gnodet](https://github.com/gnodet))
- Make sure the maven.home and maven.conf properties are correctly set … [\#573](https://github.com/apache/maven-mvnd/pull/573) ([gnodet](https://github.com/gnodet))
- Remove previous MavenProject class, \#561 [\#566](https://github.com/apache/maven-mvnd/pull/566) ([gnodet](https://github.com/gnodet))
- updates maven from 3.6.3 to 3.8.4 [\#562](https://github.com/apache/maven-mvnd/pull/562) ([lkwg82](https://github.com/lkwg82))
- Upgrades logback to the newest version to fix CVE-2021-42550 [\#557](https://github.com/apache/maven-mvnd/pull/557) ([kornelrabczak](https://github.com/kornelrabczak))
- fix stream leak [\#555](https://github.com/apache/maven-mvnd/pull/555) ([lujiefsi](https://github.com/lujiefsi))
- Fix url cache [\#532](https://github.com/apache/maven-mvnd/pull/532) ([gnodet](https://github.com/gnodet))
- Fix resident extensions [\#531](https://github.com/apache/maven-mvnd/pull/531) ([gnodet](https://github.com/gnodet))
- Upgrade to maven 3.8.4 [\#524](https://github.com/apache/maven-mvnd/pull/524) ([gnodet](https://github.com/gnodet))
- Mvnd with file locking [\#508](https://github.com/apache/maven-mvnd/pull/508) ([cstamas](https://github.com/cstamas))

## [0.7.1](https://github.com/apache/maven-mvnd/tree/0.7.1) (2021-12-07)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.7.0...0.7.1)

**Closed issues:**

- mvnd modifies original output when using quiet flag [\#513](https://github.com/apache/maven-mvnd/issues/513)
- Different java versions for mvnd and maven [\#512](https://github.com/apache/maven-mvnd/issues/512)
- bad CPU type in executable: mvnd [\#510](https://github.com/apache/maven-mvnd/issues/510)
- Pipe does not output anything [\#519](https://github.com/apache/maven-mvnd/issues/519)
- mvnd --status - output in columns is too narrow [\#518](https://github.com/apache/maven-mvnd/issues/518)
- -T/--threads is ignored in 0.7.0, only -Dmvnd.threads works [\#515](https://github.com/apache/maven-mvnd/issues/515)

**Merged pull requests:**

- Fix passing options, fixes \#515 [\#520](https://github.com/apache/maven-mvnd/pull/520) ([gnodet](https://github.com/gnodet))
- Bump xstream from 1.4.17 to 1.4.18 [\#460](https://github.com/apache/maven-mvnd/pull/460) ([dependabot[bot]](https://github.com/apps/dependabot))
- Replace the locking spy with locking in the MojoExecutor [\#523](https://github.com/apache/maven-mvnd/pull/523) ([gnodet](https://github.com/gnodet))
- mvnd status output columns are too narrow, fixes \#518 [\#522](https://github.com/apache/maven-mvnd/pull/522) ([gnodet](https://github.com/gnodet))
- Fix client not responding when using help with an output redirection, fixes \#519 [\#521](https://github.com/apache/maven-mvnd/pull/521) ([gnodet](https://github.com/gnodet))
- Update Provisio plugin [\#517](https://github.com/apache/maven-mvnd/pull/517) ([cstamas](https://github.com/cstamas))
- Upgrade GraalVM version used [\#509](https://github.com/apache/maven-mvnd/pull/509) ([gnodet](https://github.com/gnodet))
- Update mvnd to include Maven Resolver 1.7 [\#507](https://github.com/apache/maven-mvnd/pull/507) ([cstamas](https://github.com/cstamas))

## [0.7.0](https://github.com/apache/maven-mvnd/tree/0.7.0) (2021-10-20)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.6.0...0.7.0)

**Closed issues:**

- PATH computation problems with Cygwin [\#499](https://github.com/apache/maven-mvnd/issues/499)
- Support Maven 3.8.3 [\#488](https://github.com/apache/maven-mvnd/issues/488)
- `IllegalStateException` on multi module project failure [\#486](https://github.com/apache/maven-mvnd/issues/486)
- The `maven.multiModuleProjectDirectory` is badly set when using `-f [path-to-pom]` [\#484](https://github.com/apache/maven-mvnd/issues/484)
- Negative local Maven repo lookup persists after installing the artifact [\#482](https://github.com/apache/maven-mvnd/issues/482)
- Build fails on a tycho-based eclipse project [\#477](https://github.com/apache/maven-mvnd/issues/477)
- No native library found for os.name=Linux, os.arch=aarch64 [\#474](https://github.com/apache/maven-mvnd/issues/474)
- ASCII Colors not rendered under Cygwin [\#456](https://github.com/apache/maven-mvnd/issues/456)

**Merged pull requests:**

- Upgrade to GraalVM 21.3.0 and JDK 17 [\#506](https://github.com/apache/maven-mvnd/pull/506) ([gnodet](https://github.com/gnodet))
- I499 [\#505](https://github.com/apache/maven-mvnd/pull/505) ([gnodet](https://github.com/gnodet))
- NativeLoader code cleanup [\#504](https://github.com/apache/maven-mvnd/pull/504) ([gnodet](https://github.com/gnodet))
- Add support for linux-armv6 [\#503](https://github.com/apache/maven-mvnd/pull/503) ([gnodet](https://github.com/gnodet))
- Support mac-arm64 platform [\#502](https://github.com/apache/maven-mvnd/pull/502) ([gnodet](https://github.com/gnodet))
- Upgrade JLine to 3.21.0 [\#501](https://github.com/apache/maven-mvnd/pull/501) ([gnodet](https://github.com/gnodet))
- Add thread stack size \(-Xss\) option [\#489](https://github.com/apache/maven-mvnd/pull/489) ([Apanatshka](https://github.com/Apanatshka))
- Provide a local \(semaphore based\) sync context and use it as the default [\#480](https://github.com/apache/maven-mvnd/pull/480) ([gnodet](https://github.com/gnodet))
- Use a single jni.h and use more recent headers clearly labelled as GP… [\#479](https://github.com/apache/maven-mvnd/pull/479) ([gnodet](https://github.com/gnodet))
- Make sure the plugin eviction pattern also applies to extensions, fixes \#477 [\#478](https://github.com/apache/maven-mvnd/pull/478) ([gnodet](https://github.com/gnodet))
- Add instructions for MacPorts [\#476](https://github.com/apache/maven-mvnd/pull/476) ([breun](https://github.com/breun))
- Fix native-image mapping for aarch64, \#474 [\#475](https://github.com/apache/maven-mvnd/pull/475) ([lanmaoxinqing](https://github.com/lanmaoxinqing))
- Negative local Maven repo lookup persists after installing the artifa… [\#495](https://github.com/apache/maven-mvnd/pull/495) ([gnodet](https://github.com/gnodet))
- The maven.multiModuleProjectDirectory is badly set when using -f \[pat… [\#494](https://github.com/apache/maven-mvnd/pull/494) ([gnodet](https://github.com/gnodet))
- Support Maven 3.8.3, fixes \#488 [\#493](https://github.com/apache/maven-mvnd/pull/493) ([gnodet](https://github.com/gnodet))
- Fix mvnd command line aliases, fixes \#490 [\#491](https://github.com/apache/maven-mvnd/pull/491) ([gnodet](https://github.com/gnodet))
- Download required native JNI headers [\#481](https://github.com/apache/maven-mvnd/pull/481) ([gnodet](https://github.com/gnodet))

## [0.6.0](https://github.com/apache/maven-mvnd/tree/0.6.0) (2021-09-07)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.5.2...0.6.0)

**Implemented enhancements:**

- The build time event spy should aggregate values for each mojo [\#430](https://github.com/apache/maven-mvnd/issues/430)
- Display the current build status [\#361](https://github.com/apache/maven-mvnd/issues/361)
- Provide a way to remove decoration on the standard out/err streams [\#356](https://github.com/apache/maven-mvnd/issues/356)

**Fixed bugs:**

- The IPC sync context fails instead of reconnecting [\#446](https://github.com/apache/maven-mvnd/issues/446)
- Progress is computed incorrectly \(above 100%\) [\#443](https://github.com/apache/maven-mvnd/issues/443)
- Incorrect charset display in Terminal output [\#441](https://github.com/apache/maven-mvnd/issues/441)

**Closed issues:**

- Mvnd runs on several projects while mvn only runs on the top level project [\#464](https://github.com/apache/maven-mvnd/issues/464)
- Better progress report [\#463](https://github.com/apache/maven-mvnd/issues/463)
- Support Maven 3.8.2 [\#457](https://github.com/apache/maven-mvnd/issues/457)
- use unix domain socket if available [\#417](https://github.com/apache/maven-mvnd/issues/417)
- Could not find artifact org.apache.maven.surefire:surefire-providers:pom:2.22.2 [\#281](https://github.com/apache/maven-mvnd/issues/281)

**Merged pull requests:**

- Speed improvements [\#472](https://github.com/apache/maven-mvnd/pull/472) ([gnodet](https://github.com/gnodet))
- Mvn 3.8.2 support, fixes \#457 [\#471](https://github.com/apache/maven-mvnd/pull/471) ([gnodet](https://github.com/gnodet))
- Revert "\#457 Support Maven 3.8.2" [\#470](https://github.com/apache/maven-mvnd/pull/470) ([gnodet](https://github.com/gnodet))
- Disable the IPC sync context factory by default \(can be enabled using… [\#469](https://github.com/apache/maven-mvnd/pull/469) ([gnodet](https://github.com/gnodet))
- Fix CPU loop in sync server [\#468](https://github.com/apache/maven-mvnd/pull/468) ([gnodet](https://github.com/gnodet))
- Make sure mvnd does not build more projects than needed, fixes \#464 [\#465](https://github.com/apache/maven-mvnd/pull/465) ([gnodet](https://github.com/gnodet))
- Provide an early display of build failures, fixes \#361 [\#462](https://github.com/apache/maven-mvnd/pull/462) ([gnodet](https://github.com/gnodet))
- I356 [\#461](https://github.com/apache/maven-mvnd/pull/461) ([gnodet](https://github.com/gnodet))
- \#457 Support Maven 3.8.2 [\#459](https://github.com/apache/maven-mvnd/pull/459) ([robertk3s](https://github.com/robertk3s))
- Bump commons-compress from 1.20 to 1.21 [\#454](https://github.com/apache/maven-mvnd/pull/454) ([dependabot[bot]](https://github.com/apps/dependabot))
- Improve test stability on OSX [\#453](https://github.com/apache/maven-mvnd/pull/453) ([gnodet](https://github.com/gnodet))
- Make sure the client env vars are correctly propagated to system properties [\#451](https://github.com/apache/maven-mvnd/pull/451) ([gnodet](https://github.com/gnodet))
- I417 [\#450](https://github.com/apache/maven-mvnd/pull/450) ([gnodet](https://github.com/gnodet))
- Provide an eviction pattern to get rid of classloaders for bad behavi… [\#448](https://github.com/apache/maven-mvnd/pull/448) ([gnodet](https://github.com/gnodet))
- Make sure the IpcClient recreates the server if the context creation fails, fixes \#446 [\#447](https://github.com/apache/maven-mvnd/pull/447) ([gnodet](https://github.com/gnodet))
- Forked projects are counted as projects leading to incorrect count, fixes \#443 [\#444](https://github.com/apache/maven-mvnd/pull/444) ([gnodet](https://github.com/gnodet))
- Fix incorrect charset display in Terminal output, \#441 [\#442](https://github.com/apache/maven-mvnd/pull/442) ([lanmaoxinqing](https://github.com/lanmaoxinqing))

## [0.5.2](https://github.com/apache/maven-mvnd/tree/0.5.2) (2021-06-18)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.5.1...0.5.2)

**Fixed bugs:**

- The IpcSyncContextTest takes a long time to exit [\#434](https://github.com/apache/maven-mvnd/issues/434)
- java.lang.ArrayIndexOutOfBoundsException during tests [\#433](https://github.com/apache/maven-mvnd/issues/433)
- Bad string size caused by missing bytes in registry.bin [\#432](https://github.com/apache/maven-mvnd/issues/432)
- The JDK\_JAVA\_OPTIONS environment variable is not honoured [\#429](https://github.com/apache/maven-mvnd/issues/429)
- The environment set up does not work well on JDK \>= 16 [\#427](https://github.com/apache/maven-mvnd/issues/427)

**Merged pull requests:**

- Attempt to fix bad registry errors, fixes \#432 and \#433 [\#439](https://github.com/apache/maven-mvnd/pull/439) ([gnodet](https://github.com/gnodet))
- Add a system property to configure the idle timeout on the ipc sync s… [\#437](https://github.com/apache/maven-mvnd/pull/437) ([gnodet](https://github.com/gnodet))
- The JDK\_JAVA\_OPTIONS environment variable is not honoured, fixes \#429 [\#436](https://github.com/apache/maven-mvnd/pull/436) ([gnodet](https://github.com/gnodet))
- The build time event spy should aggregate values for each mojo \#430 [\#431](https://github.com/apache/maven-mvnd/pull/431) ([gnodet](https://github.com/gnodet))
- JDK 16 support, fixes \#427 [\#428](https://github.com/apache/maven-mvnd/pull/428) ([gnodet](https://github.com/gnodet))

## [0.5.1](https://github.com/apache/maven-mvnd/tree/0.5.1) (2021-06-04)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.5.0...0.5.1)

**Fixed bugs:**

- The IPC sync context fails after one minute [\#424](https://github.com/apache/maven-mvnd/issues/424)
- Can not set environment correctly on JDK \< 11 [\#422](https://github.com/apache/maven-mvnd/issues/422)
- Fix concurrent build of projects when using forked lifecycles [\#419](https://github.com/apache/maven-mvnd/issues/419)
- Parallel build fails due to missing JAR artifacts in compilePath [\#418](https://github.com/apache/maven-mvnd/issues/418)

**Merged pull requests:**

- The IPC server shuts down after one minute during the build [\#425](https://github.com/apache/maven-mvnd/pull/425) ([gnodet](https://github.com/gnodet))
- Fix the environment update, fixes \#422 [\#423](https://github.com/apache/maven-mvnd/pull/423) ([gnodet](https://github.com/gnodet))
- Fix concurrent build of projects when using forked lifecycles [\#421](https://github.com/apache/maven-mvnd/pull/421) ([gnodet](https://github.com/gnodet))
- Parallel build fails due to missing JAR artifacts in compilePath [\#420](https://github.com/apache/maven-mvnd/pull/420) ([gnodet](https://github.com/gnodet))

## [0.5.0](https://github.com/apache/maven-mvnd/tree/0.5.0) (2021-05-31)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.4.3...0.5.0)

**Implemented enhancements:**

- Add the --color option as an alias to -Dstyle.color= [\#376](https://github.com/apache/maven-mvnd/issues/376)
- autokill if inactive since some time + memory becomes low? [\#364](https://github.com/apache/maven-mvnd/issues/364)

**Closed issues:**

- The client should reserve lines to avoid hops in the output display [\#414](https://github.com/apache/maven-mvnd/issues/414)
- java.util.ConcurrentModificationException in the cache [\#405](https://github.com/apache/maven-mvnd/issues/405)
- Provide a native library for mvnd specific needs [\#400](https://github.com/apache/maven-mvnd/issues/400)
- openapi-generator-maven-plugin:5.0.1:generate  Unable to read location `src/main/openapi/project.yaml` [\#397](https://github.com/apache/maven-mvnd/issues/397)
- Support Maven 3.8.1 [\#393](https://github.com/apache/maven-mvnd/issues/393)
- \[Security\] Possible RCE [\#390](https://github.com/apache/maven-mvnd/issues/390)
- The build time spy sometimes prints info for other modules [\#389](https://github.com/apache/maven-mvnd/issues/389)
- NPE on Windows using "Git for Windows" \(MINGW\) [\#387](https://github.com/apache/maven-mvnd/issues/387)
- Support -r / --resume option [\#351](https://github.com/apache/maven-mvnd/issues/351)
- Compilation fails when using a plugin and try to provide additional dependencies to that plugin [\#276](https://github.com/apache/maven-mvnd/issues/276)

**Merged pull requests:**

- Bump xstream from 1.4.16 to 1.4.17 [\#412](https://github.com/apache/maven-mvnd/pull/412) ([dependabot[bot]](https://github.com/apps/dependabot))
- Fix ConcurrentModificationException in the cache, fixes \#405 [\#406](https://github.com/apache/maven-mvnd/pull/406) ([gnodet](https://github.com/gnodet))
- README.adoc: add Chocolatey installation option [\#398](https://github.com/apache/maven-mvnd/pull/398) ([jeffjensen](https://github.com/jeffjensen))
- Use a proper property different from the maven-buildtime-extension on… [\#396](https://github.com/apache/maven-mvnd/pull/396) ([gnodet](https://github.com/gnodet))
- \#393 update Maven version to 3.8.1 [\#394](https://github.com/apache/maven-mvnd/pull/394) ([robertk3s](https://github.com/robertk3s))
- Bump xstream from 1.4.15 to 1.4.16 [\#385](https://github.com/apache/maven-mvnd/pull/385) ([dependabot[bot]](https://github.com/apps/dependabot))
- Support -r / --resume option, fixes \#351 [\#413](https://github.com/apache/maven-mvnd/pull/413) ([gnodet](https://github.com/gnodet))
- Improve events reporting for forked lifecycles [\#411](https://github.com/apache/maven-mvnd/pull/411) ([gnodet](https://github.com/gnodet))
- Add the --color option as an alias to -Dstyle.color, fixes \#376 [\#404](https://github.com/apache/maven-mvnd/pull/404) ([gnodet](https://github.com/gnodet))
- Upgrade formatter / impsort plugins [\#403](https://github.com/apache/maven-mvnd/pull/403) ([gnodet](https://github.com/gnodet))
- Add an expiration strategy if the system has less than 5% memory available, fixes \#364 [\#402](https://github.com/apache/maven-mvnd/pull/402) ([gnodet](https://github.com/gnodet))
- Provide a native library, fixes \#400 [\#401](https://github.com/apache/maven-mvnd/pull/401) ([gnodet](https://github.com/gnodet))
- Upgrade maven 3.8.1 + prototype for global lock [\#399](https://github.com/apache/maven-mvnd/pull/399) ([gnodet](https://github.com/gnodet))
- \#390 Restrict usage of mvnd daemons to the current user by utilizing a token check [\#391](https://github.com/apache/maven-mvnd/pull/391) ([Syquel](https://github.com/Syquel))
- Global mvn settings.xml via mvnd.properties \#383 [\#386](https://github.com/apache/maven-mvnd/pull/386) ([mgoldschmidt-ds](https://github.com/mgoldschmidt-ds))
- Do not run server threads as daemon as it causes problems with the exec-maven-plugin \(\#276\) [\#330](https://github.com/apache/maven-mvnd/pull/330) ([gnodet](https://github.com/gnodet))

## [0.4.3](https://github.com/apache/maven-mvnd/tree/0.4.3) (2021-03-19)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.4.2...0.4.3)

**Fixed bugs:**

- The update of the environment does not work with jdk 16 [\#380](https://github.com/apache/maven-mvnd/issues/380)
- Segmentation fault on startup after updating to 0.4.2 [\#375](https://github.com/apache/maven-mvnd/issues/375)
- NPE from InvalidatingPluginArtifactsCache, similar to \#347 [\#377](https://github.com/apache/maven-mvnd/pull/377) ([lanmaoxinqing](https://github.com/lanmaoxinqing))

**Closed issues:**

- Release 0.4.3 [\#382](https://github.com/apache/maven-mvnd/issues/382)

**Merged pull requests:**

- The update of the environment does not work with jdk 16, fixes \#380 [\#381](https://github.com/apache/maven-mvnd/pull/381) ([gnodet](https://github.com/gnodet))
- Upgrade to jansi 2.3.2, fixes \#375 [\#378](https://github.com/apache/maven-mvnd/pull/378) ([gnodet](https://github.com/gnodet))

## [0.4.2](https://github.com/apache/maven-mvnd/tree/0.4.2) (2021-03-10)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.4.1...0.4.2)

**Fixed bugs:**

- A daemon started with -B/--batch option stays colorless forever [\#373](https://github.com/apache/maven-mvnd/issues/373)
- Sometimes starts more threads than it should? [\#362](https://github.com/apache/maven-mvnd/issues/362)
- Fix color output for file / tty [\#358](https://github.com/apache/maven-mvnd/issues/358)
- The system property mvnd.terminalWidth is missing - when starting mvnd [\#354](https://github.com/apache/maven-mvnd/issues/354)
- Make sure the environment is properly updated [\#352](https://github.com/apache/maven-mvnd/issues/352)
-  Access is denied Exception on "mvnd -version" [\#349](https://github.com/apache/maven-mvnd/issues/349)
- `mvnd` ignores the `.mvn/jvm.config` file [\#348](https://github.com/apache/maven-mvnd/issues/348)
- NPE from InvalidatingProjectArtifactsCache when building a Quarkus deployment module from a tag [\#347](https://github.com/apache/maven-mvnd/issues/347)
- --quiet seems to supress mvn output entirely in some cases [\#344](https://github.com/apache/maven-mvnd/issues/344)
- cancellation of 'mvnd qurkus:dev' with CRTL+C let the process live [\#343](https://github.com/apache/maven-mvnd/issues/343)

**Closed issues:**

- Build failing with a NPE [\#372](https://github.com/apache/maven-mvnd/issues/372)
- Release 0.4.2 [\#369](https://github.com/apache/maven-mvnd/issues/369)
- Environment mismatches should ignore the PWD var [\#234](https://github.com/apache/maven-mvnd/issues/234)
- maven-checkstyle-plugin: NoSuchMethodError: 'void org.slf4j.spi.LocationAwareLogger.log\(org.slf4j.Marker, java.lang.String, int, java.lang.String, java.lang.Throwable\)' [\#183](https://github.com/apache/maven-mvnd/issues/183)

**Merged pull requests:**

- Leverage Maven's -Dstyle.color to avoid coloring instead of stripping the ASCII codes in the client [\#371](https://github.com/apache/maven-mvnd/pull/371) ([ppalaga](https://github.com/ppalaga))
- Fix typo in README [\#370](https://github.com/apache/maven-mvnd/pull/370) ([findepi](https://github.com/findepi))
- Fix display showing more projects than the ones actually active [\#367](https://github.com/apache/maven-mvnd/pull/367) ([gnodet](https://github.com/gnodet))
- Fix color output for file / tty \#358 [\#359](https://github.com/apache/maven-mvnd/pull/359) ([gnodet](https://github.com/gnodet))
- Kill children processes when interrupting the build, fixes \#343 [\#357](https://github.com/apache/maven-mvnd/pull/357) ([gnodet](https://github.com/gnodet))
- Added known limitations to use -rf maven option [\#350](https://github.com/apache/maven-mvnd/pull/350) ([valdar](https://github.com/valdar))

## [0.4.1](https://github.com/apache/maven-mvnd/tree/0.4.1) (2021-01-25)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.4.0...0.4.1)

## [0.4.0](https://github.com/apache/maven-mvnd/tree/0.4.0) (2021-01-25)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.3.0...0.4.0)

**Closed issues:**

- \[ERROR\] Multiple entries with same key [\#333](https://github.com/apache/maven-mvnd/issues/333)
- NPE after pressing CTRL+B in the client [\#324](https://github.com/apache/maven-mvnd/issues/324)
- Readme: "mvnd specific options" is incomplete [\#316](https://github.com/apache/maven-mvnd/issues/316)
- Should print id of daemon that is processing the request [\#314](https://github.com/apache/maven-mvnd/issues/314)
- The caches are not all cleaned when deleting the local repository [\#312](https://github.com/apache/maven-mvnd/issues/312)
- Speeding up parallel plugins setup [\#310](https://github.com/apache/maven-mvnd/issues/310)
- The output of ConsoleMavenTransferListener looks ugly in the client [\#284](https://github.com/apache/maven-mvnd/issues/284)
- Cannot resolve type description for java.sql.Blob [\#277](https://github.com/apache/maven-mvnd/issues/277)
- Remove mvnd.builder.rule\* and mvnd.builder.rules.provider.\* features [\#264](https://github.com/apache/maven-mvnd/issues/264)
- Investigate the other caches in Maven [\#237](https://github.com/apache/maven-mvnd/issues/237)

**Merged pull requests:**

- Cleanup [\#339](https://github.com/apache/maven-mvnd/pull/339) ([ppalaga](https://github.com/ppalaga))
- Fix JVM resource loading from plugins [\#338](https://github.com/apache/maven-mvnd/pull/338) ([gnodet](https://github.com/gnodet))
- Fix error when the reactor contains duplicate groupId:artifactId, fix… [\#335](https://github.com/apache/maven-mvnd/pull/335) ([gnodet](https://github.com/gnodet))
- README: Mention --help in 'specific options' [\#332](https://github.com/apache/maven-mvnd/pull/332) ([famod](https://github.com/famod))
- Fixes [\#329](https://github.com/apache/maven-mvnd/pull/329) ([gnodet](https://github.com/gnodet))
- The caches are not all cleaned when deleting the local repository, fi… [\#328](https://github.com/apache/maven-mvnd/pull/328) ([gnodet](https://github.com/gnodet))
- Use mvnd instead of mvn in the help output [\#327](https://github.com/apache/maven-mvnd/pull/327) ([ppalaga](https://github.com/ppalaga))
- Add a bit of doc for the various supported keys, \#293 [\#326](https://github.com/apache/maven-mvnd/pull/326) ([gnodet](https://github.com/gnodet))
- NPE after pressing CTRL+B in the client \#324 [\#325](https://github.com/apache/maven-mvnd/pull/325) ([ppalaga](https://github.com/ppalaga))
- Fixup 143f4f13 Display the daemon id and shorten it a bit [\#323](https://github.com/apache/maven-mvnd/pull/323) ([ppalaga](https://github.com/ppalaga))
- Fixup 28ffaea Send transfer events to the client for better display [\#322](https://github.com/apache/maven-mvnd/pull/322) ([ppalaga](https://github.com/ppalaga))
- Fix TestUtils imports [\#321](https://github.com/apache/maven-mvnd/pull/321) ([famod](https://github.com/famod))
- Display the daemon id and shorten it a bit, fixes \#314 [\#318](https://github.com/apache/maven-mvnd/pull/318) ([gnodet](https://github.com/gnodet))
- One more attempt to workaround \#281 [\#317](https://github.com/apache/maven-mvnd/pull/317) ([ppalaga](https://github.com/ppalaga))
- Add TOC to README.adoc [\#315](https://github.com/apache/maven-mvnd/pull/315) ([famod](https://github.com/famod))
- Speed up parallel plugin setup \#310 [\#311](https://github.com/apache/maven-mvnd/pull/311) ([gnodet](https://github.com/gnodet))
- Send transfer events to the client for better display, \#284 [\#313](https://github.com/apache/maven-mvnd/pull/313) ([gnodet](https://github.com/gnodet))

## [0.3.0](https://github.com/apache/maven-mvnd/tree/0.3.0) (2021-01-07)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.2.0...0.3.0)

**Closed issues:**

- Messages sent from the server to the client should not end with a \r on windows [\#304](https://github.com/apache/maven-mvnd/issues/304)
- Extension support fails to resolve dependencies [\#300](https://github.com/apache/maven-mvnd/issues/300)
- Speed up bash completion loading by packaging it as a file in the ZIP distribution [\#296](https://github.com/apache/maven-mvnd/issues/296)
- Associate standard output/error streams to a particular plugin execution to have it in the rolling windows [\#295](https://github.com/apache/maven-mvnd/issues/295)
- Right-pad projectIds to improve mojo readability in the threaded view [\#288](https://github.com/apache/maven-mvnd/issues/288)
- DAG width wrong for triple interdependent graph [\#287](https://github.com/apache/maven-mvnd/issues/287)
- Support short variants of boolean properties [\#279](https://github.com/apache/maven-mvnd/issues/279)
- mvnd fails when version range is used in extensions.xml [\#275](https://github.com/apache/maven-mvnd/issues/275)
- Support launching processes with inherited IO [\#241](https://github.com/apache/maven-mvnd/issues/241)

**Merged pull requests:**

- Replace mvnd --completion by a plain source now the bash file is in t… [\#308](https://github.com/apache/maven-mvnd/pull/308) ([rmannibucau](https://github.com/rmannibucau))
- Speed up bash completion loading by packaging it as a file in the ZIP… [\#307](https://github.com/apache/maven-mvnd/pull/307) ([ppalaga](https://github.com/ppalaga))
- The output of ConsoleMavenTransferListener looks ugly in the client  [\#306](https://github.com/apache/maven-mvnd/pull/306) ([ppalaga](https://github.com/ppalaga))
- Messages sent from the server to the client should not end with a \r … [\#305](https://github.com/apache/maven-mvnd/pull/305) ([ppalaga](https://github.com/ppalaga))
- Fix extension resolution that use jdk activation, fixes \#300 [\#303](https://github.com/apache/maven-mvnd/pull/303) ([gnodet](https://github.com/gnodet))
- Rename AbstractLoggingSpy to ClientDispatcher and move it to org.mvndaemon.mvnd.daemon [\#302](https://github.com/apache/maven-mvnd/pull/302) ([ppalaga](https://github.com/ppalaga))
- Eliminate mutable global state in AbstractLoggingSpy [\#301](https://github.com/apache/maven-mvnd/pull/301) ([ppalaga](https://github.com/ppalaga))
- Support launching processes with inherited IO, fixes \#241 [\#298](https://github.com/apache/maven-mvnd/pull/298) ([gnodet](https://github.com/gnodet))
- Fix logging to use an inheritable thread local, fixes \#295 [\#297](https://github.com/apache/maven-mvnd/pull/297) ([gnodet](https://github.com/gnodet))
- Document how to install bash completion [\#290](https://github.com/apache/maven-mvnd/pull/290) ([famod](https://github.com/famod))
- Right-pad projectIds to improve mojo readability in the threaded view  [\#289](https://github.com/apache/maven-mvnd/pull/289) ([ppalaga](https://github.com/ppalaga))
- DAG width wrong for parent runtime deployment triple [\#286](https://github.com/apache/maven-mvnd/pull/286) ([ppalaga](https://github.com/ppalaga))
- Less global and mutable state [\#285](https://github.com/apache/maven-mvnd/pull/285) ([ppalaga](https://github.com/ppalaga))
- Support short variants of boolean properties [\#280](https://github.com/apache/maven-mvnd/pull/280) ([ppalaga](https://github.com/ppalaga))

## [0.2.0](https://github.com/apache/maven-mvnd/tree/0.2.0) (2020-12-16)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.1.1...0.2.0)

**Implemented enhancements:**

- Use client terminal width to format help [\#251](https://github.com/apache/maven-mvnd/issues/251)
- Add a `--serial/-1` command option to toggle maven-like behavior [\#248](https://github.com/apache/maven-mvnd/issues/248)
- Let -h/--help display also mvnd specific options [\#243](https://github.com/apache/maven-mvnd/issues/243)
- Bash completion [\#215](https://github.com/apache/maven-mvnd/issues/215)

**Closed issues:**

- mvnd.rb not support mac now [\#273](https://github.com/apache/maven-mvnd/issues/273)
- Crash with Java 17 [\#272](https://github.com/apache/maven-mvnd/issues/272)
- Maven-like rolling output when the build happens to be linear [\#269](https://github.com/apache/maven-mvnd/issues/269)
- Support Homebrew on Linux [\#268](https://github.com/apache/maven-mvnd/issues/268)
- A new daemon is always started with OpenJDK 8 [\#266](https://github.com/apache/maven-mvnd/issues/266)
- Fix `getCurrentProject()` [\#262](https://github.com/apache/maven-mvnd/issues/262)
- No reuse of daemons, no build speedup. [\#261](https://github.com/apache/maven-mvnd/issues/261)
- No reuse of daemons - error on daemon creation - unknown signal TSTP \(Windows 10, Bellsoft Liberica JDK\) [\#260](https://github.com/apache/maven-mvnd/issues/260)
- mvnd is affected by CVE-2020-17521 vulnerability [\#259](https://github.com/apache/maven-mvnd/issues/259)
- Document --status, --stop and --purge in -h/--help [\#249](https://github.com/apache/maven-mvnd/issues/249)
- The mvnd client creates a mvnd.daemonStorage\_IS\_UNDEFINED folder [\#246](https://github.com/apache/maven-mvnd/issues/246)
- mvnd --help produces no output [\#238](https://github.com/apache/maven-mvnd/issues/238)
- Wrong display of number of projects to build [\#236](https://github.com/apache/maven-mvnd/issues/236)
- IllegalStateException: Failed to lock offset 0 of .../daemon/registry.bin within 20.0 seconds [\#102](https://github.com/apache/maven-mvnd/issues/102)

**Merged pull requests:**

- Maven-like rolling output when the build happens to be linear [\#271](https://github.com/apache/maven-mvnd/pull/271) ([ppalaga](https://github.com/ppalaga))
- A new daemon is always started on Java 8  [\#267](https://github.com/apache/maven-mvnd/pull/267) ([ppalaga](https://github.com/ppalaga))
- Deprecate mvnd.builder.rule\* and mvnd.builder.rules.provider.\* features [\#265](https://github.com/apache/maven-mvnd/pull/265) ([ppalaga](https://github.com/ppalaga))
- Fix getCurrentProject, \#fixes \#262 [\#263](https://github.com/apache/maven-mvnd/pull/263) ([gnodet](https://github.com/gnodet))
- Fix SERIAL command line option [\#257](https://github.com/apache/maven-mvnd/pull/257) ([gnodet](https://github.com/gnodet))
- Bash completion  [\#255](https://github.com/apache/maven-mvnd/pull/255) ([ppalaga](https://github.com/ppalaga))
- Maven like behaviour and other small improvements [\#253](https://github.com/apache/maven-mvnd/pull/253) ([gnodet](https://github.com/gnodet))
- Use client terminal width to format help [\#252](https://github.com/apache/maven-mvnd/pull/252) ([gnodet](https://github.com/gnodet))
- Document --status, --stop and --purge in -h/--help  [\#250](https://github.com/apache/maven-mvnd/pull/250) ([ppalaga](https://github.com/ppalaga))
- Fix the mvnd.sh client log configuration, fixes \#246 [\#247](https://github.com/apache/maven-mvnd/pull/247) ([gnodet](https://github.com/gnodet))
- Fix project name and number of projects displayed on the client, fixe… [\#245](https://github.com/apache/maven-mvnd/pull/245) ([gnodet](https://github.com/gnodet))
- Let -h/--help display also mvnd specific options \#243 [\#244](https://github.com/apache/maven-mvnd/pull/244) ([ppalaga](https://github.com/ppalaga))
- mvnd --help produces no output \#238 [\#242](https://github.com/apache/maven-mvnd/pull/242) ([ppalaga](https://github.com/ppalaga))
- Upgrade JLine [\#258](https://github.com/apache/maven-mvnd/pull/258) ([gnodet](https://github.com/gnodet))
- Bump groovy.version from 3.0.0 to 3.0.7 [\#254](https://github.com/apache/maven-mvnd/pull/254) ([dependabot[bot]](https://github.com/apps/dependabot))

## [0.1.1](https://github.com/apache/maven-mvnd/tree/0.1.1) (2020-11-27)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.1.0...0.1.1)

**Closed issues:**

- mvn -Dmvnd.noBuffering=true has no effect [\#239](https://github.com/apache/maven-mvnd/issues/239)

**Merged pull requests:**

- mvn -Dmvnd.noBuffering=true has no effect  [\#240](https://github.com/apache/maven-mvnd/pull/240) ([ppalaga](https://github.com/ppalaga))

## [0.1.0](https://github.com/apache/maven-mvnd/tree/0.1.0) (2020-11-18)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/i218.t...0.1.0)

**Implemented enhancements:**

- The daemon created by the client should survive if the client is interrupted using Ctrl+C [\#193](https://github.com/apache/maven-mvnd/issues/193)

**Closed issues:**

- Move mvnd logback config file into \[MVND\_HOME\]/conf and use the standard name for the mvn specific config file [\#226](https://github.com/apache/maven-mvnd/issues/226)
- Duration properties are not passed correctly to the daemon [\#225](https://github.com/apache/maven-mvnd/issues/225)
- When the build does not produce any output, the elapsed time can be sluggish [\#224](https://github.com/apache/maven-mvnd/issues/224)
- Use the `mvnd.` prefix for all property names [\#221](https://github.com/apache/maven-mvnd/issues/221)
- Exit code not propagated from the daemon to mvnd client [\#220](https://github.com/apache/maven-mvnd/issues/220)
- pom.xml changes not honored \(post 0.0.10 regression\) [\#218](https://github.com/apache/maven-mvnd/issues/218)
- Messages associated with projectId from the previous build in the terminal and daemon log [\#216](https://github.com/apache/maven-mvnd/issues/216)
- Provide an automatic purge of daemon logs [\#196](https://github.com/apache/maven-mvnd/issues/196)
- Document that mvnd may conflict with oh-my-zsh's alias for `mvn deploy` [\#148](https://github.com/apache/maven-mvnd/issues/148)
- ${my.property:-default} style defaults defined in logback.xml do not work [\#39](https://github.com/apache/maven-mvnd/issues/39)

**Merged pull requests:**

- Upgrade to GraalVM 20.3.0 [\#235](https://github.com/apache/maven-mvnd/pull/235) ([ppalaga](https://github.com/ppalaga))
- Output revision with -v/--version [\#233](https://github.com/apache/maven-mvnd/pull/233) ([ppalaga](https://github.com/ppalaga))
- Avoid environment lookups and value conversions on hot paths [\#232](https://github.com/apache/maven-mvnd/pull/232) ([ppalaga](https://github.com/ppalaga))
- Use more recent version of DeLaGuardo/setup-graalvm action [\#230](https://github.com/apache/maven-mvnd/pull/230) ([gnodet](https://github.com/gnodet))
- Make sure our CachingProjectBuilder is used, fixes \#218 [\#228](https://github.com/apache/maven-mvnd/pull/228) ([gnodet](https://github.com/gnodet))
- Move mvnd logback config file into \[MVND\_HOME\]/conf and use the stand… [\#227](https://github.com/apache/maven-mvnd/pull/227) ([gnodet](https://github.com/gnodet))
- Clean the names of properties, fixes \#221 [\#223](https://github.com/apache/maven-mvnd/pull/223) ([gnodet](https://github.com/gnodet))
- Exit code not propagated from the daemon to mvnd client [\#222](https://github.com/apache/maven-mvnd/pull/222) ([ppalaga](https://github.com/ppalaga))
- Messages associated with projectId from the previous build in the ter… [\#217](https://github.com/apache/maven-mvnd/pull/217) ([ppalaga](https://github.com/ppalaga))
- Ignore INT and TSTP signals in the daemon [\#214](https://github.com/apache/maven-mvnd/pull/214) ([gnodet](https://github.com/gnodet))
- Automatic purge of daemon logs [\#213](https://github.com/apache/maven-mvnd/pull/213) ([gnodet](https://github.com/gnodet))

## [i218.t](https://github.com/apache/maven-mvnd/tree/i218.t) (2020-11-13)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.0.12...i218.t)

## [0.0.12](https://github.com/apache/maven-mvnd/tree/0.0.12) (2020-11-12)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.0.11...0.0.12)

**Implemented enhancements:**

- Fail fast if the daemon can not be started [\#162](https://github.com/apache/maven-mvnd/issues/162)
- Client: have just one event queue and one consuming thread [\#133](https://github.com/apache/maven-mvnd/issues/133)
- Opt out of implicit -T [\#132](https://github.com/apache/maven-mvnd/issues/132)
- Better support for dumb terminals [\#131](https://github.com/apache/maven-mvnd/issues/131)
- Option to default to a simple log when using a single thread [\#116](https://github.com/apache/maven-mvnd/issues/116)

**Fixed bugs:**

- Should support core extensions [\#114](https://github.com/apache/maven-mvnd/issues/114)

**Closed issues:**

- NoClassDefFoundError: org.slf4j.LoggerFactory [\#200](https://github.com/apache/maven-mvnd/issues/200)
- Support multiple level of properties file, discriminate between daemons, allow configuring min/max memory [\#188](https://github.com/apache/maven-mvnd/issues/188)
- Support for interactive sessions [\#180](https://github.com/apache/maven-mvnd/issues/180)
- Do not create runtime files/directories in installation directory [\#179](https://github.com/apache/maven-mvnd/issues/179)
- Allow passing additional jvm args to the daemon [\#174](https://github.com/apache/maven-mvnd/issues/174)
- mvndaemon.org domain transfer [\#153](https://github.com/apache/maven-mvnd/issues/153)
- Implement build cancellation [\#127](https://github.com/apache/maven-mvnd/issues/127)
- Provide a `mvnd.daemon` option to disable daemon for easier debugging [\#43](https://github.com/apache/maven-mvnd/issues/43)

**Merged pull requests:**

- Store registry under ~/.m2 where we already have mvnd.properties [\#211](https://github.com/apache/maven-mvnd/pull/211) ([ppalaga](https://github.com/ppalaga))
- Make TerminalOutput.dumb final, activate TerminalOutput.noBuffering with -B/--batch-mode, mvnd.rollingWindowSize default 0 [\#209](https://github.com/apache/maven-mvnd/pull/209) ([ppalaga](https://github.com/ppalaga))
- Fix the readInputLoop so that messages are all delivered and processe… [\#205](https://github.com/apache/maven-mvnd/pull/205) ([gnodet](https://github.com/gnodet))
- Improve display with an easy opt-out option and support for dumb term… [\#204](https://github.com/apache/maven-mvnd/pull/204) ([gnodet](https://github.com/gnodet))
- Minor improvements [\#203](https://github.com/apache/maven-mvnd/pull/203) ([gnodet](https://github.com/gnodet))
- Pad the status line elements so that they do not move as the build is progressing [\#202](https://github.com/apache/maven-mvnd/pull/202) ([ppalaga](https://github.com/ppalaga))
- Reduce the number of Message subclasses [\#201](https://github.com/apache/maven-mvnd/pull/201) ([ppalaga](https://github.com/ppalaga))
- Implement build cancelation [\#199](https://github.com/apache/maven-mvnd/pull/199) ([ppalaga](https://github.com/ppalaga))
- Client: have just one event queue and one consuming thread  [\#198](https://github.com/apache/maven-mvnd/pull/198) ([ppalaga](https://github.com/ppalaga))
- Non daemon option, fixes \#43 [\#197](https://github.com/apache/maven-mvnd/pull/197) ([gnodet](https://github.com/gnodet))
- Minor refactorings [\#192](https://github.com/apache/maven-mvnd/pull/192) ([ppalaga](https://github.com/ppalaga))
- Allow passing additional jvm args to the daemon, fixes \#174 [\#191](https://github.com/apache/maven-mvnd/pull/191) ([gnodet](https://github.com/gnodet))
- Refactor [\#190](https://github.com/apache/maven-mvnd/pull/190) ([gnodet](https://github.com/gnodet))
- Refactor usage of properties in the client / daemon, fixes \#188 [\#189](https://github.com/apache/maven-mvnd/pull/189) ([gnodet](https://github.com/gnodet))
- Support for interactive sessions \#180 [\#187](https://github.com/apache/maven-mvnd/pull/187) ([gnodet](https://github.com/gnodet))
- Add JVM memory expiration checks, use a specific timeout for checks [\#186](https://github.com/apache/maven-mvnd/pull/186) ([gnodet](https://github.com/gnodet))
- Fix spelling error in console logs [\#185](https://github.com/apache/maven-mvnd/pull/185) ([dsyer](https://github.com/dsyer))
- Deliver the same slf4j version as Maven 3.6.3 and manage jcl-over-slf… [\#184](https://github.com/apache/maven-mvnd/pull/184) ([ppalaga](https://github.com/ppalaga))
- Issue 162 [\#182](https://github.com/apache/maven-mvnd/pull/182) ([gnodet](https://github.com/gnodet))
- Issue 114 [\#181](https://github.com/apache/maven-mvnd/pull/181) ([gnodet](https://github.com/gnodet))
- Separate BuildStarted message to avoid serializing via Propertries.\[l… [\#178](https://github.com/apache/maven-mvnd/pull/178) ([ppalaga](https://github.com/ppalaga))
- More fine grained status on build start [\#177](https://github.com/apache/maven-mvnd/pull/177) ([ppalaga](https://github.com/ppalaga))
- User's preference for -T can be stored as mvnd.threads in ~/.m2/mvnd.… [\#176](https://github.com/apache/maven-mvnd/pull/176) ([ppalaga](https://github.com/ppalaga))

## [0.0.11](https://github.com/apache/maven-mvnd/tree/0.0.11) (2020-10-29)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.0.10...0.0.11)

**Fixed bugs:**

- Problem with the native client running in Cygwin [\#156](https://github.com/apache/maven-mvnd/issues/156)
- Killed or crashed daemon process kept in the registry until mvnd --stop is called [\#154](https://github.com/apache/maven-mvnd/issues/154)

**Closed issues:**

- ProjectBuildLogAppender not found when starting the daemon [\#165](https://github.com/apache/maven-mvnd/issues/165)
- mvnd --status complains about Unexpected output of ps -o rss= when the process is not alive anymore [\#163](https://github.com/apache/maven-mvnd/issues/163)
- mvnd native executable is not passing -Dkey=val to the daemon [\#157](https://github.com/apache/maven-mvnd/issues/157)
- Messages bigger than 65535 utf code points crash the server [\#155](https://github.com/apache/maven-mvnd/issues/155)
- Add a spinner, progress or something indicating that the build is going on [\#150](https://github.com/apache/maven-mvnd/issues/150)
- Provide a homebrew package [\#106](https://github.com/apache/maven-mvnd/issues/106)
- Warning "Unable to create a system terminal" when running maven daemon [\#36](https://github.com/apache/maven-mvnd/issues/36)

**Merged pull requests:**

- Cygwin support, fixes \#156 [\#173](https://github.com/apache/maven-mvnd/pull/173) ([gnodet](https://github.com/gnodet))
- Improve terminal output [\#172](https://github.com/apache/maven-mvnd/pull/172) ([ppalaga](https://github.com/ppalaga))
- Fixup 67d5b4b Remove leftovers [\#170](https://github.com/apache/maven-mvnd/pull/170) ([ppalaga](https://github.com/ppalaga))
- Improvements [\#169](https://github.com/apache/maven-mvnd/pull/169) ([gnodet](https://github.com/gnodet))
- Use a single cache removal strategy [\#168](https://github.com/apache/maven-mvnd/pull/168) ([gnodet](https://github.com/gnodet))
- ProjectBuildLogAppender not found when starting the daemon \#165 [\#166](https://github.com/apache/maven-mvnd/pull/166) ([ppalaga](https://github.com/ppalaga))
- Killed or crashed daemon process kept in the registry until mvnd --st… [\#164](https://github.com/apache/maven-mvnd/pull/164) ([ppalaga](https://github.com/ppalaga))
- mvnd native executable is not passing -Dkey=val to the daemon [\#159](https://github.com/apache/maven-mvnd/pull/159) ([ppalaga](https://github.com/ppalaga))
- Improve the error message that reports a daemon crash [\#158](https://github.com/apache/maven-mvnd/pull/158) ([ppalaga](https://github.com/ppalaga))
- Upgrade to jansi 2.0, fix windows output [\#151](https://github.com/apache/maven-mvnd/pull/151) ([gnodet](https://github.com/gnodet))

## [0.0.10](https://github.com/apache/maven-mvnd/tree/0.0.10) (2020-10-26)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.0.9...0.0.10)

**Closed issues:**

- mvnd --status throws NumberFormatException in 0.0.9 [\#147](https://github.com/apache/maven-mvnd/issues/147)

**Merged pull requests:**

- mvnd --status throws NumberFormatException in 0.0.9 [\#149](https://github.com/apache/maven-mvnd/pull/149) ([ppalaga](https://github.com/ppalaga))
- Add Twitter badge to README [\#146](https://github.com/apache/maven-mvnd/pull/146) ([ppalaga](https://github.com/ppalaga))
- Mention Homebrew tap in the README, show asciinema cast instead of a … [\#145](https://github.com/apache/maven-mvnd/pull/145) ([ppalaga](https://github.com/ppalaga))

## [0.0.9](https://github.com/apache/maven-mvnd/tree/0.0.9) (2020-10-25)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.0.8...0.0.9)

**Closed issues:**

- Split daemon module into daemon and dist [\#130](https://github.com/apache/maven-mvnd/issues/130)
- mvnd --status to display memory usage [\#129](https://github.com/apache/maven-mvnd/issues/129)
- Test with two threads at least [\#128](https://github.com/apache/maven-mvnd/issues/128)
- Warn if the environment of the client does not match the environment of the daemon [\#122](https://github.com/apache/maven-mvnd/issues/122)
- Give a meaningful name to the mvnd process [\#118](https://github.com/apache/maven-mvnd/issues/118)
- Building... output detail of how many modules in total and left to build [\#112](https://github.com/apache/maven-mvnd/issues/112)
- Run with min 1 cpu core left to the user [\#111](https://github.com/apache/maven-mvnd/issues/111)
- Connection timeout when trying to execute any build [\#63](https://github.com/apache/maven-mvnd/issues/63)
- Client hangs forever if the daemon crashes [\#47](https://github.com/apache/maven-mvnd/issues/47)

**Merged pull requests:**

- Remove the superfluous Serializer interface and its implemetation [\#141](https://github.com/apache/maven-mvnd/pull/141) ([ppalaga](https://github.com/ppalaga))
- Do not add mvnd-client.jar to daemon's class path [\#140](https://github.com/apache/maven-mvnd/pull/140) ([ppalaga](https://github.com/ppalaga))
- Have unique test project module names [\#139](https://github.com/apache/maven-mvnd/pull/139) ([ppalaga](https://github.com/ppalaga))
- Split daemon module into daemon and dist \#130 [\#138](https://github.com/apache/maven-mvnd/pull/138) ([ppalaga](https://github.com/ppalaga))
- Polish client status line [\#137](https://github.com/apache/maven-mvnd/pull/137) ([ppalaga](https://github.com/ppalaga))
- mvnd --status to display memory usage \#129 [\#136](https://github.com/apache/maven-mvnd/pull/136) ([ppalaga](https://github.com/ppalaga))
- Test with two threads at least \#128 [\#135](https://github.com/apache/maven-mvnd/pull/135) ([ppalaga](https://github.com/ppalaga))
- Simplify logging [\#134](https://github.com/apache/maven-mvnd/pull/134) ([ppalaga](https://github.com/ppalaga))
- Improvements [\#126](https://github.com/apache/maven-mvnd/pull/126) ([gnodet](https://github.com/gnodet))
- Display warning in case of environment mismatch \#122 [\#125](https://github.com/apache/maven-mvnd/pull/125) ([gnodet](https://github.com/gnodet))
- Improvements [\#124](https://github.com/apache/maven-mvnd/pull/124) ([gnodet](https://github.com/gnodet))
- Issue 47 [\#123](https://github.com/apache/maven-mvnd/pull/123) ([gnodet](https://github.com/gnodet))
- Fixup \#111 Document the number of utilized cores and use 1 core at least [\#121](https://github.com/apache/maven-mvnd/pull/121) ([ppalaga](https://github.com/ppalaga))
- Rename ServerMain to MavenDaemon to be more explicit, fixes \#118 [\#120](https://github.com/apache/maven-mvnd/pull/120) ([gnodet](https://github.com/gnodet))
- Leave 1 processor unused on the daemon by default, fixes \#111 [\#119](https://github.com/apache/maven-mvnd/pull/119) ([gnodet](https://github.com/gnodet))
- Improve progress display [\#113](https://github.com/apache/maven-mvnd/pull/113) ([gnodet](https://github.com/gnodet))
- Skip tests when releasing [\#110](https://github.com/apache/maven-mvnd/pull/110) ([ppalaga](https://github.com/ppalaga))

## [0.0.8](https://github.com/apache/maven-mvnd/tree/0.0.8) (2020-10-19)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.0.7...0.0.8)

**Closed issues:**

- Unnecessary directory in the 0.0.7 zip archive [\#107](https://github.com/apache/maven-mvnd/issues/107)

**Merged pull requests:**

- Upload the artifacts from the correct directory [\#109](https://github.com/apache/maven-mvnd/pull/109) ([ppalaga](https://github.com/ppalaga))
- Unnecessary directory in the 0.0.7 zip archive \#107 [\#108](https://github.com/apache/maven-mvnd/pull/108) ([ppalaga](https://github.com/ppalaga))

## [0.0.7](https://github.com/apache/maven-mvnd/tree/0.0.7) (2020-10-19)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.0.6...0.0.7)

**Closed issues:**

- The system streams should be captured and redirected to the client with a per-thread association to the module being build [\#100](https://github.com/apache/maven-mvnd/issues/100)
- Isolate the integration tests from the local environment [\#97](https://github.com/apache/maven-mvnd/issues/97)
- Add mvn.cmd [\#93](https://github.com/apache/maven-mvnd/issues/93)
- Test a scenario using mvn [\#92](https://github.com/apache/maven-mvnd/issues/92)
- Re-layout the distro so that mvn is not in bin [\#91](https://github.com/apache/maven-mvnd/issues/91)
- Replace deprecated GitHub actions commands [\#85](https://github.com/apache/maven-mvnd/issues/85)
- The output of modules being built in parallel is interleaved [\#78](https://github.com/apache/maven-mvnd/issues/78)
- Show test output while running [\#77](https://github.com/apache/maven-mvnd/issues/77)
- Explain project better in README [\#75](https://github.com/apache/maven-mvnd/issues/75)
- The test output is missing in the console [\#21](https://github.com/apache/maven-mvnd/issues/21)

**Merged pull requests:**

- Issue 100 [\#105](https://github.com/apache/maven-mvnd/pull/105) ([gnodet](https://github.com/gnodet))
- Replace deprecated GitHub actions commands \#85 [\#104](https://github.com/apache/maven-mvnd/pull/104) ([ppalaga](https://github.com/ppalaga))
- Isolate the integration tests from the local environment [\#101](https://github.com/apache/maven-mvnd/pull/101) ([ppalaga](https://github.com/ppalaga))
- Partial revert to fix windows integration test [\#99](https://github.com/apache/maven-mvnd/pull/99) ([gnodet](https://github.com/gnodet))
- Add NOTICE LICENSE and README to the distro [\#98](https://github.com/apache/maven-mvnd/pull/98) ([ppalaga](https://github.com/ppalaga))
- Re-layout the distro so that mvn is not in bin [\#96](https://github.com/apache/maven-mvnd/pull/96) ([ppalaga](https://github.com/ppalaga))
- Test a scenario using mvn \#92 [\#95](https://github.com/apache/maven-mvnd/pull/95) ([ppalaga](https://github.com/ppalaga))
- Improvements [\#94](https://github.com/apache/maven-mvnd/pull/94) ([gnodet](https://github.com/gnodet))
- Honor the -X / --debug / --quiet arguments on the command line [\#90](https://github.com/apache/maven-mvnd/pull/90) ([gnodet](https://github.com/gnodet))
- Fix mvn [\#89](https://github.com/apache/maven-mvnd/pull/89) ([gnodet](https://github.com/gnodet))
- Fix display [\#88](https://github.com/apache/maven-mvnd/pull/88) ([gnodet](https://github.com/gnodet))
- Use Visual Studio 2019 pre-installed on Windows CI workers to save some [\#84](https://github.com/apache/maven-mvnd/pull/84) ([ppalaga](https://github.com/ppalaga))
- Use a maven proxy for integration tests to speed them up [\#83](https://github.com/apache/maven-mvnd/pull/83) ([gnodet](https://github.com/gnodet))
- Improvements [\#81](https://github.com/apache/maven-mvnd/pull/81) ([gnodet](https://github.com/gnodet))
- Replace the jpm library with the jdk ProcessHandle interface, \#36 [\#80](https://github.com/apache/maven-mvnd/pull/80) ([gnodet](https://github.com/gnodet))
- Provide smarter output on the client, fixes \#77 [\#79](https://github.com/apache/maven-mvnd/pull/79) ([gnodet](https://github.com/gnodet))
- Explain project better in README \#75 [\#76](https://github.com/apache/maven-mvnd/pull/76) ([ppalaga](https://github.com/ppalaga))

## [0.0.6](https://github.com/apache/maven-mvnd/tree/0.0.6) (2020-09-29)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.0.5...0.0.6)

**Closed issues:**

- CachingProjectBuilder ignored [\#72](https://github.com/apache/maven-mvnd/issues/72)
- Keep a changelog file [\#64](https://github.com/apache/maven-mvnd/issues/64)

**Merged pull requests:**

- Wait for the deamon to become idle before rebuilding in UpgradesInBom… [\#74](https://github.com/apache/maven-mvnd/pull/74) ([ppalaga](https://github.com/ppalaga))
- Added a changelog automatic update gh action [\#70](https://github.com/apache/maven-mvnd/pull/70) ([oscerd](https://github.com/oscerd))
- Fixup publishing new versions via sdkman vendor API \#67 [\#69](https://github.com/apache/maven-mvnd/pull/69) ([ppalaga](https://github.com/ppalaga))
-  CachingProjectBuilder ignored [\#73](https://github.com/apache/maven-mvnd/pull/73) ([ppalaga](https://github.com/ppalaga))

## [0.0.5](https://github.com/apache/maven-mvnd/tree/0.0.5) (2020-09-17)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.0.4...0.0.5)

**Closed issues:**

- Publish new versions via sdkman vendor API [\#67](https://github.com/apache/maven-mvnd/issues/67)
- Cannot re-use daemon with sdkman java 8.0.265.hs-adpt [\#65](https://github.com/apache/maven-mvnd/issues/65)
- List mvnd on sdkman.io [\#48](https://github.com/apache/maven-mvnd/issues/48)

**Merged pull requests:**

- Publish new versions via sdkman vendor API [\#68](https://github.com/apache/maven-mvnd/pull/68) ([ppalaga](https://github.com/ppalaga))
- Cannot re-use daemon with sdkman java 8.0.265.hs-adpt [\#66](https://github.com/apache/maven-mvnd/pull/66) ([ppalaga](https://github.com/ppalaga))
- Upgrade to GraalVM 20.2.0 [\#62](https://github.com/apache/maven-mvnd/pull/62) ([ppalaga](https://github.com/ppalaga))

## [0.0.4](https://github.com/apache/maven-mvnd/tree/0.0.4) (2020-08-20)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.0.3...0.0.4)

**Merged pull requests:**

- Allow \<mvnd.builder.rule\> entries to be separated by whitespace [\#61](https://github.com/apache/maven-mvnd/pull/61) ([ppalaga](https://github.com/ppalaga))

## [0.0.3](https://github.com/apache/maven-mvnd/tree/0.0.3) (2020-08-15)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.0.2...0.0.3)

**Closed issues:**

- Require Java 8+ instead of Java 11+ at runtime [\#56](https://github.com/apache/maven-mvnd/issues/56)
- Using MAVEN\_HOME may clash with other tools [\#53](https://github.com/apache/maven-mvnd/issues/53)
- Could not notify CliPluginRealmCache [\#49](https://github.com/apache/maven-mvnd/issues/49)

**Merged pull requests:**

- Use amd64 arch label also on Mac [\#58](https://github.com/apache/maven-mvnd/pull/58) ([ppalaga](https://github.com/ppalaga))

## [0.0.2](https://github.com/apache/maven-mvnd/tree/0.0.2) (2020-08-14)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.0.1...0.0.2)

**Merged pull requests:**

- Fix \#56 Require Java 8+ instead of Java 11+ at runtime [\#57](https://github.com/apache/maven-mvnd/pull/57) ([ppalaga](https://github.com/ppalaga))
- Include native clients in platform specific distros [\#55](https://github.com/apache/maven-mvnd/pull/55) ([ppalaga](https://github.com/ppalaga))
- Fix \#53 Using MAVEN\_HOME may clash with other tools [\#54](https://github.com/apache/maven-mvnd/pull/54) ([ppalaga](https://github.com/ppalaga))
- Add curl -L flag to cope with redirects [\#51](https://github.com/apache/maven-mvnd/pull/51) ([fvaleri](https://github.com/fvaleri))
- Fix \#49 Could not notify CliPluginRealmCache [\#50](https://github.com/apache/maven-mvnd/pull/50) ([ppalaga](https://github.com/ppalaga))

## [0.0.1](https://github.com/apache/maven-mvnd/tree/0.0.1) (2020-07-30)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/0.0.0.0...0.0.1)

**Closed issues:**

- mvnd fails if there is no .mvn/ dir in the user home [\#42](https://github.com/apache/maven-mvnd/issues/42)
- Cannot clean on Windows as long as mvnd keeps a plugin from the tree loaded [\#40](https://github.com/apache/maven-mvnd/issues/40)
- Maven mojo change ignored [\#33](https://github.com/apache/maven-mvnd/issues/33)
- differences between `mvn clean install` and `mvnd clean install` [\#25](https://github.com/apache/maven-mvnd/issues/25)

**Merged pull requests:**

- Fix \#42 mvnd fails if there is no .mvn/ dir in the user home [\#46](https://github.com/apache/maven-mvnd/pull/46) ([ppalaga](https://github.com/ppalaga))
- Fix \#40 Cannot clean on Windows as long as mvnd keeps a plugin from t… [\#45](https://github.com/apache/maven-mvnd/pull/45) ([ppalaga](https://github.com/ppalaga))
- Add code formatter plugins [\#44](https://github.com/apache/maven-mvnd/pull/44) ([ppalaga](https://github.com/ppalaga))

## [0.0.0.0](https://github.com/apache/maven-mvnd/tree/0.0.0.0) (2020-06-21)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/200611-client-logging-54e2c6ef...0.0.0.0)

## [200611-client-logging-54e2c6ef](https://github.com/apache/maven-mvnd/tree/200611-client-logging-54e2c6ef) (2019-09-27)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/200611-client-logging-15f559eb...200611-client-logging-54e2c6ef)

## [200611-client-logging-15f559eb](https://github.com/apache/maven-mvnd/tree/200611-client-logging-15f559eb) (2019-09-27)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/200611-client-logging-bdf5f8af...200611-client-logging-15f559eb)

## [200611-client-logging-bdf5f8af](https://github.com/apache/maven-mvnd/tree/200611-client-logging-bdf5f8af) (2019-09-27)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/200611-client-logging-f2a61e8c...200611-client-logging-bdf5f8af)

## [200611-client-logging-f2a61e8c](https://github.com/apache/maven-mvnd/tree/200611-client-logging-f2a61e8c) (2019-09-27)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/200611-client-logging-5b542cab...200611-client-logging-f2a61e8c)

## [200611-client-logging-5b542cab](https://github.com/apache/maven-mvnd/tree/200611-client-logging-5b542cab) (2019-09-27)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/200611-client-logging-bc063301...200611-client-logging-5b542cab)

## [200611-client-logging-bc063301](https://github.com/apache/maven-mvnd/tree/200611-client-logging-bc063301) (2019-09-27)

[Full Changelog](https://github.com/apache/maven-mvnd/compare/844f3ddd7f4278b2ba097d817def4c3b46d574e7...200611-client-logging-bc063301)



\* *This Changelog was automatically generated by [github_changelog_generator](https://github.com/github-changelog-generator/github-changelog-generator)*
