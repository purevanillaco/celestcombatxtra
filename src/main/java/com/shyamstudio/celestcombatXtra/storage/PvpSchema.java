package com.shyamstudio.celestcombatXtra.storage;

/**
 * Table/column names and per-dialect SQL for the pvp_state table.
 */
final class PvpSchema {
    private PvpSchema() {}

    static final String TABLE = "pvp_state";

    static final String CREATE_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS pvp_state (
                player_uuid TEXT PRIMARY KEY,
                pvp_enabled INTEGER NOT NULL,
                updated_at BIGINT NOT NULL
            )
            """;

    static final String CREATE_TABLE_MARIADB = """
            CREATE TABLE IF NOT EXISTS pvp_state (
                player_uuid VARCHAR(36) PRIMARY KEY,
                pvp_enabled TINYINT(1) NOT NULL,
                updated_at BIGINT NOT NULL
            )
            """;

    static final String SELECT_STATE =
            "SELECT pvp_enabled FROM pvp_state WHERE player_uuid = ?";

    static final String UPSERT_SQLITE = """
            INSERT INTO pvp_state (player_uuid, pvp_enabled, updated_at) VALUES (?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET pvp_enabled = excluded.pvp_enabled, updated_at = excluded.updated_at
            """;

    static final String UPSERT_MARIADB = """
            INSERT INTO pvp_state (player_uuid, pvp_enabled, updated_at) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE pvp_enabled = VALUES(pvp_enabled), updated_at = VALUES(updated_at)
            """;
}
