CREATE TABLE IF NOT EXISTS simulated_transaction (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  attempt_ref VARCHAR(64) NOT NULL,
  order_ref BIGINT NOT NULL,
  settlement_version INT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_transaction_attempt (attempt_ref),
  CONSTRAINT ck_transaction_amount CHECK (amount >= 0),
  CONSTRAINT ck_transaction_status CHECK (status IN ('PROCESSING','SUCCESS','FAILED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS simulated_transaction_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  transaction_id BIGINT NOT NULL,
  old_status VARCHAR(20) NULL,
  new_status VARCHAR(20) NOT NULL,
  actor_label VARCHAR(80) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX ix_transaction_event (transaction_id, created_at),
  CONSTRAINT fk_transaction_event FOREIGN KEY (transaction_id) REFERENCES simulated_transaction(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
