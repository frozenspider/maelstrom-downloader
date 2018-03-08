maelstrom-downloader
=======================

[![Build Status](https://travis-ci.org/frozenspider/maelstrom-downloader.svg?branch=master)](https://travis-ci.org/frozenspider/maelstrom-downloader)
[![codecov](https://codecov.io/gh/frozenspider/maelstrom-downloader/branch/master/graph/badge.svg)](https://codecov.io/gh/frozenspider/maelstrom-downloader)

Advanced cross-platform download manager.

Requires Java (at least version 8).


Building
--------
`sbt buildDistr` will generate a runnable JAR along with Windows binaries
and Linux shell script.

Note that generating Windows binaries requires [Launch4j](http://launch4j.sourceforge.net/)
and system environment variable `LAUNCH4J_HOME` pointing to it. 


Changelog
---------

See [CHANGELOG.md](CHANGELOG.md)
