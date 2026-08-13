#!/usr/bin/env bash
set -euo pipefail

candidate_env="/etc/orangejd/mysql-selfhost.env"
test -r "$candidate_env"
# shellcheck disable=SC1090
source "$candidate_env"

MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  --ssl-mode=REQUIRED \
  --host="$MYSQL_HOST" \
  --port="$MYSQL_PORT" \
  --user="$MYSQL_USER" \
  --database=orangevc_rehearsal <<'SQL'
SHOW SESSION STATUS LIKE 'Ssl_cipher';
SELECT COUNT(*) AS table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'orangevc_rehearsal';
SELECT COUNT(*) AS failed_flyway_migrations
FROM flyway_schema_history
WHERE success = 0;
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 1;
START TRANSACTION;
CREATE TEMPORARY TABLE migration_write_probe (
  id BIGINT PRIMARY KEY,
  probe_value VARCHAR(32) NOT NULL
);
INSERT INTO migration_write_probe (id, probe_value) VALUES (1, 'orangevc-migration-probe');
UPDATE migration_write_probe SET probe_value = 'validated' WHERE id = 1;
SELECT COUNT(*) AS write_probe_count FROM migration_write_probe WHERE probe_value = 'validated';
ROLLBACK;
SQL
