--liquibase formatted sql
--changeset mentee-power-team:001-create-schema
--comment: Создание основной схемы приложения

CREATE SCHEMA IF NOT EXISTS mentee_power;
SET search_path TO mentee_power;

--rollback DROP SCHEMA IF EXISTS mentee_power CASCADE;