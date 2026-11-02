SELECT @@version AS mysql_version,
       @@hostname AS mysql_host,
       @@require_secure_transport AS require_secure_transport,
       @@max_connections AS max_connections;

SELECT COUNT(*) AS table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'orangevc';

SELECT version, description, success
FROM orangevc.flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 1;

SELECT COUNT(*) AS active_orangevc_connections
FROM information_schema.PROCESSLIST
WHERE DB = 'orangevc';

SELECT ROUND(SUM(DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2) AS database_size_mb
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'orangevc';

SELECT COUNT(*) AS encrypted_application_connections
FROM performance_schema.status_by_thread s
JOIN performance_schema.threads t ON t.THREAD_ID = s.THREAD_ID
WHERE s.VARIABLE_NAME = 'Ssl_cipher'
  AND t.PROCESSLIST_USER = 'orangevc_app'
  AND s.VARIABLE_VALUE <> '';
