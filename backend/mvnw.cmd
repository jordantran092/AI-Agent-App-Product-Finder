@echo off
setlocal
set "MAVEN_PROJECTBASEDIR=%~dp0"
set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
if not exist "%WRAPPER_JAR%" (
    echo Maven Wrapper JAR is missing: %WRAPPER_JAR%
    exit /b 1
)
pushd "%MAVEN_PROJECTBASEDIR%"
java -Dmaven.multiModuleProjectDirectory=. -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
set "WRAPPER_EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %WRAPPER_EXIT_CODE%
