SET @ddl = IF(
  (SELECT COUNT(*)
   FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'conversation'
     AND column_name = 'hidden_by_initiator') = 0,
  'ALTER TABLE conversation ADD COLUMN hidden_by_initiator TINYINT NOT NULL DEFAULT 0 AFTER update_time',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @ddl = IF(
  (SELECT COUNT(*)
   FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'conversation'
     AND column_name = 'hidden_by_receiver') = 0,
  'ALTER TABLE conversation ADD COLUMN hidden_by_receiver TINYINT NOT NULL DEFAULT 0 AFTER hidden_by_initiator',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;
