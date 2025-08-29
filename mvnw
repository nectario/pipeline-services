#!/bin/sh
# Apache Maven Wrapper minimal script

set -e

PRG="$0"
# Resolve symlinks
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
BASEDIR=`dirname "$PRG"`
BASEDIR=`cd "$BASEDIR" >/dev/null; pwd`

WRAPPER_JAR="$BASEDIR/.mvn/wrapper/maven-wrapper.jar"

if [ -z "$JAVA_HOME" ] ; then
  JAVA_EXE="java"
else
  JAVA_EXE="$JAVA_HOME/bin/java"
fi

exec "$JAVA_EXE" -classpath "$WRAPPER_JAR" "-Dmaven.multiModuleProjectDirectory=$BASEDIR" org.apache.maven.wrapper.MavenWrapperMain "$@"

