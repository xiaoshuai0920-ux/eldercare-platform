ALTER TABLE simulated_transaction
  ADD COLUMN active_order_version VARCHAR(40)
    GENERATED ALWAYS AS (
      CASE WHEN status IN ('PROCESSING', 'SUCCESS')
        THEN CONCAT(order_ref, ':', settlement_version)
        ELSE NULL
      END
    ) STORED,
  ADD UNIQUE KEY uk_transaction_active_order_version (active_order_version);
