-- V7: append-only stock movement ledger.
-- All balance updates and their corresponding ledger INSERT must happen in
-- the same backend @Transactional operation. Lock the inventory row first.

CREATE TABLE inventory_transactions (
    id                          BIGSERIAL PRIMARY KEY,
    inventory_id                BIGINT       NOT NULL,
    transaction_type             VARCHAR(20)  NOT NULL,
    quantity_delta_on_hand       INTEGER      NOT NULL DEFAULT 0,
    quantity_delta_reserved      INTEGER      NOT NULL DEFAULT 0,
    request_id                   BIGINT,
    actor_user_id                BIGINT       NOT NULL,
    reason                       TEXT,
    external_reference           VARCHAR(100),
    is_demo                      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_inventory_transactions_inventory FOREIGN KEY (inventory_id)
        REFERENCES inventory(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_transactions_request FOREIGN KEY (request_id)
        REFERENCES need_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_transactions_actor FOREIGN KEY (actor_user_id)
        REFERENCES users(id) ON DELETE RESTRICT,

    CONSTRAINT ck_inventory_transactions_type
        CHECK (transaction_type IN
            ('RECEIPT', 'RESERVATION', 'RELEASE', 'DISPATCH', 'ADJUSTMENT', 'WRITE_OFF')),

    -- Each transaction type has one defined balance effect:
    -- RECEIPT:     on-hand increases.
    -- RESERVATION: reserved increases; request is required.
    -- RELEASE:     reserved decreases; request is required.
    -- DISPATCH:    on-hand and reserved decrease by the same quantity.
    -- ADJUSTMENT:  on-hand changes by a signed amount; reason is required.
    -- WRITE_OFF:   on-hand decreases; reason is required.
    CONSTRAINT ck_inventory_transactions_effect CHECK (
        (transaction_type = 'RECEIPT'
            AND quantity_delta_on_hand > 0
            AND quantity_delta_reserved = 0)
        OR
        (transaction_type = 'RESERVATION'
            AND quantity_delta_on_hand = 0
            AND quantity_delta_reserved > 0
            AND request_id IS NOT NULL)
        OR
        (transaction_type = 'RELEASE'
            AND quantity_delta_on_hand = 0
            AND quantity_delta_reserved < 0
            AND request_id IS NOT NULL)
        OR
        (transaction_type = 'DISPATCH'
            AND quantity_delta_on_hand < 0
            AND quantity_delta_reserved = quantity_delta_on_hand
            AND request_id IS NOT NULL)
        OR
        (transaction_type = 'ADJUSTMENT'
            AND quantity_delta_on_hand <> 0
            AND quantity_delta_reserved = 0)
        OR
        (transaction_type = 'WRITE_OFF'
            AND quantity_delta_on_hand < 0
            AND quantity_delta_reserved = 0)
    ),

    CONSTRAINT ck_inventory_transactions_reason CHECK (
        transaction_type NOT IN ('ADJUSTMENT', 'WRITE_OFF')
        OR length(btrim(COALESCE(reason, ''))) > 0
    ),

    CONSTRAINT ck_inventory_transactions_external_reference CHECK (
        external_reference IS NULL OR length(btrim(external_reference)) > 0
    )
);

CREATE INDEX idx_inventory_transactions_inventory_time
    ON inventory_transactions(inventory_id, created_at DESC, id DESC);

CREATE INDEX idx_inventory_transactions_request_time
    ON inventory_transactions(request_id, created_at DESC, id DESC)
    WHERE request_id IS NOT NULL;

CREATE INDEX idx_inventory_transactions_actor
    ON inventory_transactions(actor_user_id);

-- Ledger rows are immutable. Correct a mistake with an opposite/compensating
-- transaction; never edit or delete historical movement records.
CREATE FUNCTION reject_inventory_transaction_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'inventory_transactions is append-only; record a compensating transaction instead'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_inventory_transactions_no_update
BEFORE UPDATE ON inventory_transactions
FOR EACH ROW
EXECUTE FUNCTION reject_inventory_transaction_mutation();

CREATE TRIGGER trg_inventory_transactions_no_delete
BEFORE DELETE ON inventory_transactions
FOR EACH ROW
EXECUTE FUNCTION reject_inventory_transaction_mutation();

CREATE TRIGGER trg_inventory_transactions_no_truncate
BEFORE TRUNCATE ON inventory_transactions
FOR EACH STATEMENT
EXECUTE FUNCTION reject_inventory_transaction_mutation();
