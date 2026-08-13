#!/usr/bin/env bash
set -euo pipefail

config_source="${1:-/tmp/orangevc-mysql.cnf}"
config_target="/etc/mysql/mysql.conf.d/90-orangevc.cnf"
data_dir="/var/lib/mysql"

test -f "$config_source"
test -d "$data_dir"

business_schema_count="$(mysql -N -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME NOT IN ('information_schema','mysql','performance_schema','sys')")"
if [[ "$business_schema_count" != "0" ]]; then
  echo "Refusing to initialize a MySQL instance that already contains business schemas." >&2
  exit 1
fi

stamp="$(date +%Y%m%d-%H%M%S)"
systemctl stop mysql
mv "$data_dir" "/var/lib/mysql-initial-$stamp"
install -d -o mysql -g mysql -m 750 "$data_dir"
install -o root -g root -m 644 "$config_source" "$config_target"
mysqld --initialize-insecure --user=mysql --datadir="$data_dir"
systemctl start mysql
mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH auth_socket"
systemctl enable mysql

mysql -N -e "SELECT @@version, @@lower_case_table_names, @@character_set_server, @@collation_server, @@max_connections, @@require_secure_transport"
