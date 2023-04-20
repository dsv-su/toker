CREATE TABLE IF NOT EXISTS resource_server (
    owner VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    id CHAR(32) NOT NULL,
    secret CHAR(32) NOT NULL,
    PRIMARY KEY (id),
    INDEX I_resource_server_owner (owner)
);
