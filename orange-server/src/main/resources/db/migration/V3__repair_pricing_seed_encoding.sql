-- Repair the four immutable seed labels if an older MySQL init client decoded
-- the UTF-8 schema file as latin1. Keys and administrator-configured prices are untouched.
UPDATE ai_service_pricing SET service_name = '趋势分析 V1' WHERE service_key = 'ai:trend:v1';
UPDATE ai_service_pricing SET service_name = '趋势分析 V2' WHERE service_key = 'ai:trend:v2';
UPDATE ai_service_pricing SET service_name = '方案设计' WHERE service_key = 'ai:plan';
UPDATE ai_service_pricing SET service_name = '岗位匹配' WHERE service_key = 'ai:match';
