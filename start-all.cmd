@echo off
rem Spore-Cloud 一键启动：注册中心 -> 两个业务服务 -> 等到真的注册上 -> 网关
rem 关键：网关必须等 user/content 真正出现在注册表里再起。固定 sleep 不够，
rem 网关起早了 LoadBalancer 会缓存空实例表，之后一直 503（实测踩过）。
setlocal enabledelayedexpansion
set "BASE=%~dp0"
set "V=0.1.0-SNAPSHOT"
set "EUREKA=http://127.0.0.1:8761"

if not exist "%BASE%spore-registry\target\spore-registry-%V%.jar" (
  echo [x] 还没打包。先在 %BASE% 执行： mvn -DskipTests package
  exit /b 1
)

echo [1/4] spore-registry    :8761
start "spore-registry" /min /d "%BASE%spore-registry" java -jar target\spore-registry-%V%.jar

set /a N=0
:wait_registry
curl -s -o nul --max-time 2 %EUREKA%/ >nul 2>&1 && goto :registry_ready
set /a N+=1
if !N! GEQ 40 goto :registry_ready
ping -n 3 127.0.0.1 >nul
goto :wait_registry
:registry_ready

echo [2/4] spore-user        :19081
start "spore-user" /min /d "%BASE%spore-user" java -jar target\spore-user-%V%.jar
echo [3/4] spore-content     :19082
start "spore-content" /min /d "%BASE%spore-content" java -jar target\spore-content-%V%.jar

echo      等待两个服务注册...（最多 120 秒）
set /a N=0
:wait_services
set "J="
for /f "delims=" %%i in ('curl -s --max-time 3 -H "Accept: application/json" %EUREKA%/eureka/apps') do set "J=!J!%%i"
echo !J! | findstr /C:"SPORE-USER" >nul || goto :services_wait
echo !J! | findstr /C:"SPORE-CONTENT" >nul || goto :services_wait
goto :services_ready
:services_wait
set /a N+=1
if !N! GEQ 40 goto :services_ready
ping -n 4 127.0.0.1 >nul
goto :wait_services
:services_ready

echo [4/4] spore-gateway     :19080
start "spore-gateway" /min /d "%BASE%spore-gateway" java -jar target\spore-gateway-%V%.jar

echo.
echo 网关入口 : http://^<本机局域网IP^>:19080/api
echo 注册中心 : http://localhost:8761
echo 停止全部 : taskkill /FI "WINDOWTITLE eq spore-*"
