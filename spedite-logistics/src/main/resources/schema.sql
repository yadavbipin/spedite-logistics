ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(32);

UPDATE invoices
SET payment_status = 'PENDING'
WHERE payment_status IS NULL OR TRIM(payment_status) = '';

ALTER TABLE invoices
    ALTER COLUMN payment_status SET DEFAULT 'PENDING';

ALTER TABLE invoices
    ALTER COLUMN payment_status SET NOT NULL;

CREATE TABLE IF NOT EXISTS invoice_payments (
    payment_id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    received_at TIMESTAMP NOT NULL,
    payment_mode VARCHAR(64),
    reference_number VARCHAR(255),
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_invoice_payments_invoice_id
    ON invoice_payments(invoice_id);

CREATE INDEX IF NOT EXISTS idx_invoice_payments_received_at
    ON invoice_payments(received_at);
