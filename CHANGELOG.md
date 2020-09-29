# Changelog

## [0.0.6](https://github.com/mvndaemon/mvnd/tree/0.0.6) (2020-09-29)

[Full Changelog](https://github.com/mvndaemon/mvnd/compare/0.0.5...0.0.6)

**Closed issues:**

- CachingProjectBuilder ignored [\#72](https://github.com/mvndaemon/mvnd/issues/72)
- Goals of mvnd [\#71](https://github.com/mvndaemon/mvnd/issues/71)
- Keep a changelog file [\#64](https://github.com/mvndaemon/mvnd/issues/64)

**Merged pull requests:**

- Wait for the deamon to become idle before rebuilding in UpgradesInBom… [\#74](https://github.com/mvndaemon/mvnd/pull/74) ([ppalaga](https://github.com/ppalaga))
-  CachingProjectBuilder ignored [\#73](https://github.com/mvndaemon/mvnd/pull/73) ([ppalaga](https://github.com/ppalaga))
- Added a changelog automatic update gh action [\#70](https://github.com/mvndaemon/mvnd/pull/70) ([oscerd](https://github.com/oscerd))
- Fixup publishing new versions via sdkman vendor API \#67 [\#69](https://github.com/mvndaemon/mvnd/pull/69) ([ppalaga](https://github.com/ppalaga))

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

**Closed issues:**

- differences between `mvn clean install` and `mvnd clean install` [\#25](https://github.com/mvndaemon/mvnd/issues/25)

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

**Merged pull requests:**

- Fix \#42 mvnd fails if there is no .mvn/ dir in the user home [\#46](https://github.com/mvndaemon/mvnd/pull/46) ([ppalaga](https://github.com/ppalaga))
- Fix \#40 Cannot clean on Windows as long as mvnd keeps a plugin from t… [\#45](https://github.com/mvndaemon/mvnd/pull/45) ([ppalaga](https://github.com/ppalaga))
- Add code formatter plugins [\#44](https://github.com/mvndaemon/mvnd/pull/44) ([ppalaga](https://github.com/ppalaga))



\* *This Changelog was automatically generated by [github_changelog_generator](https://github.com/github-changelog-generator/github-changelog-generator)*
