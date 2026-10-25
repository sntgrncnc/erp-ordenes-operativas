#!/bin/bash
# Healthcheck: la BD está "lista" solo cuando el esquema de la aplicación está
# desplegado y el paquete PKG_ORDEN compilado y VÁLIDO.
sqlplus -s -L "${APP_USER}/${APP_USER_PASSWORD}@//localhost:1521/FREEPDB1" <<'SQL' | grep -q "ESQUEMA_OK"
WHENEVER SQLERROR EXIT FAILURE
SET HEADING OFF FEEDBACK OFF PAGESIZE 0
SELECT 'ESQUEMA_OK'
  FROM USER_OBJECTS
 WHERE OBJECT_NAME = 'PKG_ORDEN'
   AND OBJECT_TYPE = 'PACKAGE BODY'
   AND STATUS = 'VALID'
   AND EXISTS (SELECT 1 FROM TRANSICION_ESTADO);
EXIT
SQL
