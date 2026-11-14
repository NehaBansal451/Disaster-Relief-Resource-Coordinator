-- V6: warehouse inventory by resource and batch.
-- PostgreSQL 16 / PostGIS 3.4. This migration adds stock balances only;
-- V7 adds the append-only movement ledger.

CREATE TABLE inventory (
    id                  BIGSERIAL PRIMARY KEY,
    warehouse_id        BIGINT       NOT NULL,
    resource_id         BIGINT       NOT NULL,
    batch_number        VARCHAR(100),
    expiry_date         DATE,
    quantity_on_hand    INTEGER      NOT NULL DEFAULT 0,
    quantity_reserved   INTEGER      NOT NULL DEFAULT 0,
    is_demo             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id)
        REFERENCES warehouses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_resource FOREIGN KEY (resource_id)
        REFERENCES resources(id) ON DELETE RESTRICT,

    -- One balance row per warehouse + resource + batch. NULL batch means
    -- unbatched stock; NULLS NOT DISTINCT prevents duplicate unbatched rows.
    CONSTRAINT uq_inventory_warehouse_resource_batch
        UNIQUE NULLS NOT DISTINCT (warehouse_id, resource_id, batch_number),

    CONSTRAINT ck_inventory_batch_not_blank
        CHECK (batch_number IS NULL OR length(btrim(batch_number)) > 0),
    CONSTRAINT ck_inventory_quantity_on_hand
        CHECK (quantity_on_hand >= 0),
    CONSTRAINT ck_inventory_quantity_reserved
        CHECK (quantity_reserved >= 0 AND quantity_reserved <= quantity_on_hand)
);

-- The unique index above starts with warehouse_id, so it also supports the
-- warehouse FK and the common warehouse/resource lookup.
CREATE INDEX idx_inventory_resource
    ON inventory(resource_id);

-- Supports expiry-aware stock selection (FEFO: first expiry, first out).
CREATE INDEX idx_inventory_fefo
    ON inventory(resource_id, expiry_date, warehouse_id)
    WHERE expiry_date IS NOT NULL;

-- A perishable resource must carry both a batch identifier and an expiry date.
-- This is a cross-table rule, so it cannot be expressed as a plain CHECK.
CREATE FUNCTION enforce_inventory_perishable_batch()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_perishable BOOLEAN;
BEGIN
    SELECT perishable
      INTO v_perishable
      FROM resources
     WHERE id = NEW.resource_id;

    IF v_perishable IS TRUE
       AND (NEW.batch_number IS NULL OR length(btrim(NEW.batch_number)) = 0
            OR NEW.expiry_date IS NULL) THEN
        RAISE EXCEPTION
            'Perishable inventory requires a non-empty batch_number and expiry_date (resource_id=%)',
            NEW.resource_id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_inventory_perishable_batch
BEFORE INSERT OR UPDATE OF resource_id, batch_number, expiry_date
ON inventory
FOR EACH ROW
EXECUTE FUNCTION enforce_inventory_perishable_batch();

-- If a catalogue item is changed from non-perishable to perishable after stock
-- rows exist, require those rows to be corrected first.
CREATE FUNCTION protect_resource_perishable_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.perishable IS TRUE AND OLD.perishable IS DISTINCT FROM NEW.perishable
       AND EXISTS (
           SELECT 1
             FROM inventory i
            WHERE i.resource_id = NEW.id
              AND (i.batch_number IS NULL OR length(btrim(i.batch_number)) = 0
                   OR i.expiry_date IS NULL)
       ) THEN
        RAISE EXCEPTION
            'Cannot mark resource % as perishable until every inventory row has a batch number and expiry date',
            NEW.id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_resource_perishable_change
BEFORE UPDATE OF perishable
ON resources
FOR EACH ROW
EXECUTE FUNCTION protect_resource_perishable_change();
