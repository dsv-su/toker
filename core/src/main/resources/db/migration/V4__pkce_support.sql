ALTER TABLE code ADD COLUMN code_challenge VARCHAR(140) NULL;

ALTER TABLE client MODIFY COLUMN secret CHAR(32) NULL;
