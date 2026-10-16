@echo off
title Servidor OxiKardex
cd /d "%~dp0"
echo ============================================
echo   Servidor OxiKardex - Hospital Chulucanas
echo ============================================
echo   Iniciando... el servidor indicara la
echo   direccion para abrir la pagina.
echo   Deje esta ventana abierta mientras usa la
echo   aplicacion. Para detenerla, cierre esta
echo   ventana o presione Ctrl+C.
echo ============================================
node server.js
pause
