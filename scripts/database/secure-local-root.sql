INSTALL PLUGIN auth_socket SONAME 'auth_socket.so';
ALTER USER 'root'@'localhost' IDENTIFIED WITH auth_socket;

SELECT @@version AS version,
       @@lower_case_table_names AS lower_case_table_names,
       @@character_set_server AS character_set_server,
       @@collation_server AS collation_server,
       @@max_connections AS max_connections,
       @@require_secure_transport AS require_secure_transport,
       @@innodb_buffer_pool_size AS innodb_buffer_pool_size,
       @@innodb_redo_log_capacity AS innodb_redo_log_capacity;
