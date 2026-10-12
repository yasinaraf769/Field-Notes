ALTER TABLE customers
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_customers_version_nonnegative CHECK (version >= 0);

ALTER TABLE sites
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_sites_version_nonnegative CHECK (version >= 0);

ALTER TABLE field_notes
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_field_notes_version_nonnegative CHECK (version >= 0);

ALTER TABLE customers
    DROP CONSTRAINT fk_customers_user,
    ADD CONSTRAINT fk_customers_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE sites
    DROP CONSTRAINT fk_sites_customer,
    ADD CONSTRAINT fk_sites_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT;

ALTER TABLE field_notes
    DROP CONSTRAINT fk_field_notes_site,
    ADD CONSTRAINT fk_field_notes_site
        FOREIGN KEY (site_id) REFERENCES sites(id) ON DELETE RESTRICT;

CREATE INDEX idx_customers_user_updated_at ON customers(user_id, updated_at);
CREATE INDEX idx_sites_customer_updated_at ON sites(customer_id, updated_at);
CREATE INDEX idx_field_notes_site_updated_at ON field_notes(site_id, updated_at);
