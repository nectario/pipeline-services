@ECHO OFF
SETLOCAL

SET BASEDIR=%~dp0
SET WRAPPER_JAR=%BASEDIR%\.mvn\wrapper\maven-wrapper.jar

IF NOT "%JAVA_HOME%"=="" (
  SET JAVA_EXE="%JAVA_HOME%\bin\java.exe"
) ELSE (
  SET JAVA_EXE=java
)

"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%BASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*

ENDLOCAL

