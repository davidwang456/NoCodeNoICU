@echo off
chcp 65001
set JAVA_OPTS=-Dfile.encoding=UTF-8
java %JAVA_OPTS% -jar target/Excel2Mysql-3.8.0-SNAPSHOT.jar 