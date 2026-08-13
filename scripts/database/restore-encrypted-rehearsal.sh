#!/usr/bin/env bash
set -euo pipefail
umask 077

private_key="${1:?private key path is required}"
encrypted_file="${2:?encrypted backup path is required}"
restore_db="orangevc_restore_test"
plain_dump="$(mktemp --suffix=.sql.gz)"
source_counts="$(mktemp)"
target_counts="$(mktemp)"

cleanup() {
  mysql -e "DROP DATABASE IF EXISTS ${restore_db}" >/dev/null 2>&1 || true
  shred -u "$plain_dump" "$private_key" 2>/dev/null || true
  rm -f "$source_counts" "$target_counts"
}
trap cleanup EXIT

test -s "$private_key"
test -s "$encrypted_file"
age --decrypt -i "$private_key" -o "$plain_dump" "$encrypted_file"
gzip -t "$plain_dump"

mysql -e "DROP DATABASE IF EXISTS ${restore_db}; CREATE DATABASE ${restore_db} CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci;"
gzip -dc "$plain_dump" | mysql --default-character-set=utf8mb3 "$restore_db"

while IFS= read -r table_name; do
  mysql --database=orangevc_rehearsal --batch --skip-column-names \
    --execute="SELECT CONCAT('${table_name}', CHAR(9), COUNT(*)) FROM \`${table_name}\`" >>"$source_counts"
  mysql --database="$restore_db" --batch --skip-column-names \
    --execute="SELECT CONCAT('${table_name}', CHAR(9), COUNT(*)) FROM \`${table_name}\`" >>"$target_counts"
done < <(mysql --database=orangevc_rehearsal --batch --skip-column-names --execute='SHOW TABLES')

sort -o "$source_counts" "$source_counts"
sort -o "$target_counts" "$target_counts"
diff -u "$source_counts" "$target_counts"

table_count="$(mysql -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${restore_db}'")"
flyway_failed="$(mysql -N "$restore_db" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0")"
printf 'encrypted_restore=validated\ntable_count=%s\nflyway_failed=%s\n' "$table_count" "$flyway_failed"
