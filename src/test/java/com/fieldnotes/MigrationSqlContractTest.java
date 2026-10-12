package com.fieldnotes;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationSqlContractTest {

    @Test
    void initialMigrationContainsRelationalSchemaAndClientUuidFallbacks() throws IOException {
        String sql = readMigration("V1__initial_schema.sql");

        assertTrue(sql.contains("CREATE TABLE users"));
        assertTrue(sql.contains("CREATE TABLE customers"));
        assertTrue(sql.contains("CREATE TABLE sites"));
        assertTrue(sql.contains("CREATE TABLE field_notes"));
        assertTrue(sql.contains("CREATE TABLE refresh_tokens"));

        assertTrue(sql.contains("user_id UUID NOT NULL"));
        assertTrue(sql.contains("FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"));
        assertTrue(sql.contains("FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE"));
        assertTrue(sql.contains("FOREIGN KEY (site_id) REFERENCES sites(id) ON DELETE CASCADE"));
        assertTrue(sql.contains("id UUID PRIMARY KEY DEFAULT gen_random_uuid()"));

        assertTrue(sql.contains("CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED'))"));
        assertTrue(sql.contains("CREATE INDEX idx_field_notes_status"));
        assertTrue(sql.contains("CREATE INDEX idx_field_notes_title_search"));
    }

    @Test
    void syncMigrationAddsVersionsAndTombstonesWithoutCascadingDeletes() throws IOException {
        String sql = readMigration("V2__add_sync_tombstones_and_restrict_deletes.sql");

        assertTrue(sql.contains("ADD COLUMN deleted_at TIMESTAMPTZ"));
        assertTrue(sql.contains("ADD COLUMN version BIGINT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("REFERENCES users(id) ON DELETE RESTRICT"));
        assertTrue(sql.contains("REFERENCES customers(id) ON DELETE RESTRICT"));
        assertTrue(sql.contains("REFERENCES sites(id) ON DELETE RESTRICT"));
        assertTrue(sql.contains("idx_customers_user_updated_at"));
        assertTrue(sql.contains("idx_sites_customer_updated_at"));
        assertTrue(sql.contains("idx_field_notes_site_updated_at"));
    }

    @Test
    void deviceSyncCursorsMigrationCreatesCursorTableAndUniqueIndex() throws IOException {
        String sql = readMigration("V3__add_device_sync_cursors.sql");

        assertTrue(sql.contains("CREATE TABLE device_sync_cursors"));
        assertTrue(sql.contains("id          UUID        PRIMARY KEY DEFAULT gen_random_uuid()"));
        assertTrue(sql.contains("user_id     UUID        NOT NULL"));
        assertTrue(sql.contains("device_id   VARCHAR(255) NOT NULL"));
        assertTrue(sql.contains("last_sync_at TIMESTAMPTZ NOT NULL"));
        assertTrue(sql.contains("REFERENCES users(id) ON DELETE CASCADE"));
        assertTrue(sql.contains("UNIQUE (user_id, device_id)"));
        assertTrue(sql.contains("CREATE INDEX idx_device_sync_cursors_user_id"));
    }

    private String readMigration(String filename) throws IOException {
        var resource = new ClassPathResource("db/migration/" + filename);
        return new String(Objects.requireNonNull(resource.getInputStream().readAllBytes()), StandardCharsets.UTF_8);
    }
}
