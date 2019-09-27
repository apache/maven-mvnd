# mvnd: Maven Daemon

The mvnd project aims to provide a daemon infrastructure for maven based builds.  It borrows techniques from Gradle and Takari to provide a simple and efficient system.

## Building

```
git clone https://github.com/gnodet/mvnd.git
cd mvnd
mvn install -Pmaven-distro
```

## Configuring

```
export PATH=[mvn-root]/target/maven-distro/bin:$PATH
```

## Usage

```
mvnd install
```

This project is still in prototype mode, so feedback is most welcomed !
