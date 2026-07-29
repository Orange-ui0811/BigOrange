-- MySQL unique indexes allow multiple NULL values, so global leaderboard
-- snapshots (topic_name IS NULL) were inserted repeatedly. Keep the newest
-- snapshot in each logical group before replacing the nullable unique key.
DELETE older
FROM leaderboard_snapshot older
JOIN leaderboard_snapshot newer
  ON newer.source = older.source
 AND COALESCE(newer.topic_name, '') = COALESCE(older.topic_name, '')
 AND newer.period = older.period
 AND newer.snapshot_date = older.snapshot_date
 AND newer.id > older.id;

ALTER TABLE leaderboard_snapshot
    DROP INDEX uk_snapshot,
    ADD COLUMN topic_key VARCHAR(64)
        GENERATED ALWAYS AS (COALESCE(topic_name, '')) STORED,
    ADD UNIQUE KEY uk_snapshot_topic_key
        (source, topic_key, period, snapshot_date);
