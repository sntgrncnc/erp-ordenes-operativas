#!/bin/bash
# -----------------------------------------------------------------------------
# Inicialización del esquema de la aplicación (solo en el PRIMER arranque del
# contenedor, cuando el volumen de datos está vacío).
#
# La imagen gvenzl/oracle-free ya creó el usuario ${APP_USER} en FREEPDB1.
# Aquí ejecutamos, conectados como ese usuario, los scripts de /opt/sql en
# orden alfabético. Cada script termina con WHENEVER SQLERROR EXIT, por lo que
# un error detiene la secuencia.
# NOTA: este archivo es "sourceado" por el entrypoint: no usar 'exit' ni 'set -e'.
# -----------------------------------------------------------------------------
echo "[init-esquema] Creando esquema de la aplicación en FREEPDB1 como ${APP_USER}"

for script in /opt/sql/*.sql; do
  echo "[init-esquema] Ejecutando ${script}"
  if ! sqlplus -s -L "${APP_USER}/${APP_USER_PASSWORD}@//localhost:1521/FREEPDB1" @"${script}"; then
    echo "[init-esquema] ERROR ejecutando ${script}. Se detiene la inicialización." >&2
    break
  fi
done

echo "[init-esquema] Fin de la inicialización del esquema"
