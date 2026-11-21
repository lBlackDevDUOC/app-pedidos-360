-- Se ejecuta una sola vez, al crear el volumen de postgres-analitica.
-- report_db la crea la imagen (POSTGRES_DB); aquí se agrega la base de ms-audit.
-- Cada microservicio tiene su propia base: ninguno lee las tablas del otro.
CREATE DATABASE audit_db;
