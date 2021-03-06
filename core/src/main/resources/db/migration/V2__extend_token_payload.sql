ALTER TABLE token ADD COLUMN principal VARCHAR(64);
ALTER TABLE token ADD COLUMN display_name VARCHAR(128);
ALTER TABLE token ADD COLUMN mail VARCHAR(64);
ALTER TABLE token ADD COLUMN entitlements VARCHAR(1024);
UPDATE token SET principal = payload;
UPDATE token SET entitlements = ''; 
ALTER TABLE token DROP COLUMN payload;
ALTER TABLE token MODIFY COLUMN principal VARCHAR(64) NOT NULL;
ALTER TABLE token MODIFY COLUMN entitlements VARCHAR(1024) NOT NULL;

ALTER TABLE code ADD COLUMN principal VARCHAR(64);
ALTER TABLE code ADD COLUMN display_name VARCHAR(128);
ALTER TABLE code ADD COLUMN mail VARCHAR(64);
ALTER TABLE code ADD COLUMN entitlements VARCHAR(1024);
UPDATE code SET principal = payload;
UPDATE code SET entitlements = '';
ALTER TABLE code DROP COLUMN payload;
ALTER TABLE code MODIFY COLUMN principal VARCHAR(64) NOT NULL;
ALTER TABLE code MODIFY COLUMN entitlements VARCHAR(1024) NOT NULL;
