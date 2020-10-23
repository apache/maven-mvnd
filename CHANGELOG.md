# Changelog

## [Unreleased](https://github.com/mvndaemon/mvnd/tree/HEAD)

[Full Changelog](https://github.com/mvndaemon/mvnd/compare/0.0.8...HEAD)

**Closed issues:**

- Please give a meaningful name to the mvnd process [\#118](https://github.com/mvndaemon/mvnd/issues/118)
- Building... output detail of how many modules in total and left to build [\#112](https://github.com/mvndaemon/mvnd/issues/112)
- Run with min 1 cpu core left to the user [\#111](https://github.com/mvndaemon/mvnd/issues/111)
- Connection timeout when trying to execute any build [\#63](https://github.com/mvndaemon/mvnd/issues/63)

**Merged pull requests:**

- Improvements [\#126](https://github.com/mvndaemon/mvnd/pull/126) ([gnodet](https://github.com/gnodet))
- Display warning in case of environment mismatch \#122 [\#125](https://github.com/mvndaemon/mvnd/pull/125) ([gnodet](https://github.com/gnodet))
- Improvements [\#124](https://github.com/mvndaemon/mvnd/pull/124) ([gnodet](https://github.com/gnodet))
- Fixup \#111 Document the number of utilized cores and use 1 core at least [\#121](https://github.com/mvndaemon/mvnd/pull/121) ([ppalaga](https://github.com/ppalaga))
- Rename ServerMain to MavenDaemon to be more explicit, fixes \#118 [\#120](https://github.com/mvndaemon/mvnd/pull/120) ([gnodet](https://github.com/gnodet))
- Leave 1 processor unused on the daemon by default, fixes \#111 [\#119](https://github.com/mvndaemon/mvnd/pull/119) ([gnodet](https://github.com/gnodet))
- Improve progress display [\#113](https://github.com/mvndaemon/mvnd/pull/113) ([gnodet](https://github.com/gnodet))
- Skip tests when releasing [\#110](https://github.com/mvndaemon/mvnd/pull/110) ([ppalaga](https://github.com/ppalaga))

## [0.0.8](https://github.com/mvndaemon/mvnd/tree/0.0.8) (2020-10-19)

[Full Changelog](https://github.com/mvndaemon/mvnd/compare/0.0.7...0.0.8)

**Closed issues:**

- Unnecessary directory in the 0.0.7 zip archive [\#107](https://github.com/mvndaemon/mvnd/issues/107)

**Merged pull requests:**

- Upload the artifacts from the correct directory [\#109](https://github.com/mvndaemon/mvnd/pull/109) ([ppalaga](https://github.com/ppalaga))
- Unnecessary directory in the 0.0.7 zip archive \#107 [\#108](https://github.com/mvndaemon/mvnd/pull/108) ([ppalaga](https://github.com/ppalaga))

## [0.0.7](https://github.com/mvndaemon/mvnd/tree/0.0.7) (2020-10-19)

[Full Changelog](https://github.com/mvndaemon/mvnd/compare/0.0.6...0.0.7)

**Closed issues:**

- The system streams should be captured and redirected to the client with a per-thread association to the module being build [\#100](https://github.com/mvndaemon/mvnd/issues/100)
- Isolate the integration tests from the local environment [\#97](https://github.com/mvndaemon/mvnd/issues/97)
- Add mvn.cmd [\#93](https://github.com/mvndaemon/mvnd/issues/93)
- Test a scenario using mvn [\#92](https://github.com/mvndaemon/mvnd/issues/92)
- Re-layout the distro so that mvn is not in bin [\#91](https://github.com/mvndaemon/mvnd/issues/91)
- Replace deprecated GitHub actions commands [\#85](https://github.com/mvndaemon/mvnd/issues/85)
- The output of modules being built in parallel is interleaved [\#78](https://github.com/mvndaemon/mvnd/issues/78)
- Show test output while running [\#77](https://github.com/mvndaemon/mvnd/issues/77)
- Explain project better in README [\#75](https://github.com/mvndaemon/mvnd/issues/75)
- The test output is missing in the console [\#21](https://github.com/mvndaemon/mvnd/issues/21)

**Merged pull requests:**

- Issue 100 [\#105](https://github.com/mvndaemon/mvnd/pull/105) ([gnodet](https://github.com/gnodet))
- Replace deprecated GitHub actions commands \#85 [\#104](https://github.com/mvndaemon/mvnd/pull/104) ([ppalaga](https://github.com/ppalaga))
- Isolate the integration tests from the local environment [\#101](https://github.com/mvndaemon/mvnd/pull/101) ([ppalaga](https://github.com/ppalaga))
- Partial revert to fix windows integration test [\#99](https://github.com/mvndaemon/mvnd/pull/99) ([gnodet](https://github.com/gnodet))
- Add NOTICE LICENSE and README to the distro [\#98](https://github.com/mvndaemon/mvnd/pull/98) ([ppalaga](https://github.com/ppalaga))
- Re-layout the distro so that mvn is not in bin [\#96](https://github.com/mvndaemon/mvnd/pull/96) ([ppalaga](https://github.com/ppalaga))
- Test a scenario using mvn \#92 [\#95](https://github.com/mvndaemon/mvnd/pull/95) ([ppalaga](https://github.com/ppalaga))
- Improvements [\#94](https://github.com/mvndaemon/mvnd/pull/94) ([gnodet](https://github.com/gnodet))
- Honor the -X / --debug / --quiet arguments on the command line [\#90](https://github.com/mvndaemon/mvnd/pull/90) ([gnodet](https://github.com/gnodet))
- Fix mvn [\#89](https://github.com/mvndaemon/mvnd/pull/89) ([gnodet](https://github.com/gnodet))
- Fix display [\#88](https://github.com/mvndaemon/mvnd/pull/88) ([gnodet](https://github.com/gnodet))
- Use Visual Studio 2019 pre-installed on Windows CI workers to save some [\#84](https://github.com/mvndaemon/mvnd/pull/84) ([ppalaga](https://github.com/ppalaga))
- Use a maven proxy for integration tests to speed them up [\#83](https://github.com/mvndaemon/mvnd/pull/83) ([gnodet](https://github.com/gnodet))
- Improvements [\#81](https://github.com/mvndaemon/mvnd/pull/81) ([gnodet](https://github.com/gnodet))
- Replace the jpm library with the jdk ProcessHandle interface, \#36 [\#80](https://github.com/mvndaemon/mvnd/pull/80) ([gnodet](https://github.com/gnodet))
- Provide smarter output on the client, fixes \#77 [\#79](https://github.com/mvndaemon/mvnd/pull/79) ([gnodet](https://github.com/gnodet))
- Explain project better in README \#75 [\#76](https://github.com/mvndaemon/mvnd/pull/76) ([ppalaga](https://github.com/ppalaga))

## [0.0.6](https://github.com/mvndaemon/mvnd/tree/0.0.6) (2020-09-29)

[Full Changelog](https://github.com/mvndaemon/mvnd/compare/0.0.5...0.0.6)

**Closed issues:**

- Goals of mvnd [\#71](https://github.com/mvndaemon/mvnd/issues/71)
- CachingProjectBuilder ignored [\#72](https://github.com/mvndaemon/mvnd/issues/72)
- Keep a changelog file [\#64](https://github.com/mvndaemon/mvnd/issues/64)

**Merged pull requests:**

- Wait for the deamon to become idle before rebuilding in UpgradesInBom… [\#74](https://github.com/mvndaemon/mvnd/pull/74) ([ppalaga](https://github.com/ppalaga))
- Added a changelog automatic update gh action [\#70](https://github.com/mvndaemon/mvnd/pull/70) ([oscerd](https://github.com/oscerd))
- Fixup publishing new versions via sdkman vendor API \#67 [\#69](https://github.com/mvndaemon/mvnd/pull/69) ([ppalaga](https://github.com/ppalaga))
-  CachingProjectBuilder ignored [\#73](https://github.com/mvndaemon/mvnd/pull/73) ([ppalaga](https://github.com/ppalaga))

## [0.0.5](https://github.com/mvndaemon/mvnd/tree/0.0.5) (2020-09-17)

[Full Changelog](https://github.com/mvndaemon/mvnd/compare/0.0.4...0.0.5)

**Closed issues:**

- Publish new versions via sdkman vendor API [\#67](https://github.com/mvndaemon/mvnd/issues/67)
- Cannot re-use daemon with sdkman java 8.0.265.hs-adpt [\#65](https://github.com/mvndaemon/mvnd/issues/65)
- List mvnd on sdkman.io [\#48](https://github.com/mvndaemon/mvnd/issues/48)

**Merged pull requests:**

- Publish new versions via sdkman vendor API [\#68](https://github.com/mvndaemon/mvnd/pull/68) ([ppalaga](https://github.com/ppalaga))
- Cannot re-use daemon with sdkman java 8.0.265.hs-adpt [\#66](https://github.com/mvndaemon/mvnd/pull/66) ([ppalaga](https://github.com/ppalaga))
- Upgrade to GraalVM 20.2.0 [\#62](https://github.com/mvndaemon/mvnd/pull/62) ([ppalaga](https://github.com/ppalaga))

## [0.0.4](https://github.com/mvndaemon/mvnd/tree/0.0.4) (2020-08-20)

[Full Changelog](https://github.com/mvndaemon/mvnd/compare/0.0.3...0.0.4)

**Closed issues:**

- Question: when running mvnd, it looks like regular mvn is still running [\#60](https://github.com/mvndaemon/mvnd/issues/60)
- Question: is there a way to limit the concurrency? [\#59](https://github.com/mvndaemon/mvnd/issues/59)

**Merged pull requests:**

- Allow \<mvnd.builder.rule\> entries to be separated by whitespace [\#61](https://github.com/mvndaemon/mvnd/pull/61) ([ppalaga](https://github.com/ppalaga))

## [0.0.3](https://github.com/mvndaemon/mvnd/tree/0.0.3) (2020-08-15)

[Full Changelog](https://github.com/mvndaemon/mvnd/compare/0.0.2...0.0.3)

**Closed issues:**

- Require Java 8+ instead of Java 11+ at runtime [\#56](https://github.com/mvndaemon/mvnd/issues/56)
- Using MAVEN\_HOME may clash with other tools [\#53](https://github.com/mvndaemon/mvnd/issues/53)
- Could not notify CliPluginRealmCache [\#49](https://github.com/mvndaemon/mvnd/issues/49)

**Merged pull requests:**

- Use amd64 arch label also on Mac [\#58](https://github.com/mvndaemon/mvnd/pull/58) ([ppalaga](https://github.com/ppalaga))

## [0.0.2](https://github.com/mvndaemon/mvnd/tree/0.0.2) (2020-08-14)

[Full Changelog](https://github.com/mvndaemon/mvnd/compare/0.0.1...0.0.2)

**Merged pull requests:**

- Fix \#56 Require Java 8+ instead of Java 11+ at runtime [\#57](https://github.com/mvndaemon/mvnd/pull/57) ([ppalaga](https://github.com/ppalaga))
- Include native clients in platform specific distros [\#55](https://github.com/mvndaemon/mvnd/pull/55) ([ppalaga](https://github.com/ppalaga))
- Fix \#53 Using MAVEN\_HOME may clash with other tools [\#54](https://github.com/mvndaemon/mvnd/pull/54) ([ppalaga](https://github.com/ppalaga))
- Add curl -L flag to cope with redirects [\#51](https://github.com/mvndaemon/mvnd/pull/51) ([fvaleri](https://github.com/fvaleri))
- Fix \#49 Could not notify CliPluginRealmCache [\#50](https://github.com/mvndaemon/mvnd/pull/50) ([ppalaga](https://github.com/ppalaga))

## [0.0.1](https://github.com/mvndaemon/mvnd/tree/0.0.1) (2020-07-30)

[Full Changelog](https://github.com/mvndaemon/mvnd/compare/844f3ddd7f4278b2ba097d817def4c3b46d574e7...0.0.1)

**Closed issues:**

- Running maven daemon fails on windows [\#35](https://github.com/mvndaemon/mvnd/issues/35)
- Add some integration tests [\#23](https://github.com/mvndaemon/mvnd/issues/23)
- Class loader clash during Quarkus augmentation [\#22](https://github.com/mvndaemon/mvnd/issues/22)
- Allow to plug a custom rule resolver  [\#19](https://github.com/mvndaemon/mvnd/issues/19)
- Chaotic output if the console's height is less then the number of threads [\#17](https://github.com/mvndaemon/mvnd/issues/17)
- Concurrency issues while handling log events in the daemon [\#16](https://github.com/mvndaemon/mvnd/issues/16)
- Do not display exceptions stack trace when trying to stop already stopped daemons [\#15](https://github.com/mvndaemon/mvnd/issues/15)
- Concurrent access to the local repository [\#14](https://github.com/mvndaemon/mvnd/issues/14)
- Support for hidden dependencies [\#12](https://github.com/mvndaemon/mvnd/issues/12)
- mvnd should fail cleanly with unknown CLI options [\#11](https://github.com/mvndaemon/mvnd/issues/11)
- Properly configure logging in the client [\#10](https://github.com/mvndaemon/mvnd/issues/10)
- mvnd does not pick the BoM from the source tree [\#9](https://github.com/mvndaemon/mvnd/issues/9)
- Add a correct NOTICE file [\#8](https://github.com/mvndaemon/mvnd/issues/8)
- Do not require the mvn in PATH to come from mvnd dist [\#7](https://github.com/mvndaemon/mvnd/issues/7)
- Provide an option to kill/stop the daemon [\#6](https://github.com/mvndaemon/mvnd/issues/6)
- Options / system properties are not reset in the daemon after a build [\#5](https://github.com/mvndaemon/mvnd/issues/5)
- Add a LICENSE file [\#4](https://github.com/mvndaemon/mvnd/issues/4)
- The mvn output appears all at once at the very end [\#3](https://github.com/mvndaemon/mvnd/issues/3)
- mvnd -version does not work [\#2](https://github.com/mvndaemon/mvnd/issues/2)
- mvnd fails if there is no .mvn/ dir in the user home [\#42](https://github.com/mvndaemon/mvnd/issues/42)
- Cannot clean on Windows as long as mvnd keeps a plugin from the tree loaded [\#40](https://github.com/mvndaemon/mvnd/issues/40)
- Maven mojo change ignored [\#33](https://github.com/mvndaemon/mvnd/issues/33)
- differences between `mvn clean install` and `mvnd clean install` [\#25](https://github.com/mvndaemon/mvnd/issues/25)

**Merged pull requests:**

- Fix \#42 mvnd fails if there is no .mvn/ dir in the user home [\#46](https://github.com/mvndaemon/mvnd/pull/46) ([ppalaga](https://github.com/ppalaga))
- Fix \#40 Cannot clean on Windows as long as mvnd keeps a plugin from t… [\#45](https://github.com/mvndaemon/mvnd/pull/45) ([ppalaga](https://github.com/ppalaga))
- Add code formatter plugins [\#44](https://github.com/mvndaemon/mvnd/pull/44) ([ppalaga](https://github.com/ppalaga))



\* *This Changelog was automatically generated by [github_changelog_generator](https://github.com/github-changelog-generator/github-changelog-generator)*
