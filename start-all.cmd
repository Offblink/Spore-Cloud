@echo off
rem Spore-Cloud 一键启动：注册中心 → 两个业务服务 → 网关（网关等业务服务注册完再起）
setlocal
set "BASE=%~dp0"
set "V=0.1.0-SNAPSHOT"
set "JAR=spore-registry\target\spore-registry-%V%.jar"

if not exist "%BASE%%JAR%" (
  echo [x] 还没打包。先在 %BASE% 执行： mvn -DskipTests package
  exit /b 1
)

echo [1/4] spore-registry    :8761
start "spore-registry" /min /d "%BASE%spore-registry" java -jar target\spore-registry-%V%.jar
echo      等待注册中心就绪...
ping -n 11 127.0.0.1 >nul

echo [2/4] spore-user        :19081
start "spore-user" /min /d "%BASE%spore-user" java -jar target\spore-user-%V%.jar
echo [3/4] spore-content     :19082
start "spore-content" /min /d "%BASE%spore-content" java -jar target\spore-content-%V%.jar
echo      等待业务服务注册...
ping -n 16 127.0.0.1 >nul

echo [4/4] spore-gateway     :19080
start "spore-gateway" /min /d "%BASE%spore-gateway" java -jar target\spore-gateway-%V%.jar

echo.
echo 网关入口 : http://^<本机局域网IP^>:19080/api
echo 注册中心 : http://localhost:8761
echo 停止全部 : taskkill /FI "WINDOWTITLE eq spore-*"
