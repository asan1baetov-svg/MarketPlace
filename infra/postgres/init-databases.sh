#!/bin/bash
# Создаёт по одной БД на сервис в общем инстансе PostgreSQL (для локальной разработки).
# В проде — отдельный инстанс/кластер на сервис.
set -e

for db in auth catalog orders finance courier mlm notification; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-SQL
    SELECT 'CREATE DATABASE $db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
SQL
  echo "database '$db' ready"
done
