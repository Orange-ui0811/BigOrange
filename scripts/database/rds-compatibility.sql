SELECT 'version' AS setting_name, @@version AS setting_value
UNION ALL SELECT 'character_set_server', @@character_set_server
UNION ALL SELECT 'collation_server', @@collation_server
UNION ALL SELECT 'sql_mode', @@sql_mode
UNION ALL SELECT 'lower_case_table_names', CAST(@@lower_case_table_names AS CHAR)
UNION ALL SELECT 'time_zone', @@time_zone;

SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = DATABASE();

SELECT ENGINE, COUNT(*) AS table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
GROUP BY ENGINE;

SELECT 'tables' AS object_type, COUNT(*) AS object_count
FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
UNION ALL SELECT 'triggers', COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE()
UNION ALL SELECT 'routines', COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE()
UNION ALL SELECT 'events', COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA = DATABASE();
