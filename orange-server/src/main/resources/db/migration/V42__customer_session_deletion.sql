-- Keep run usage/audit records while removing deleted sessions from all customer access.
ALTER TABLE customer_session ADD COLUMN deleted_at DATETIME(3) NULL;
