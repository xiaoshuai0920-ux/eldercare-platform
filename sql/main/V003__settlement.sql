CREATE TABLE IF NOT EXISTS settlement (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  version INT NOT NULL DEFAULT 1,
  method VARCHAR(16) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'WAITING_METHOD',
  settled_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_settlement_order (order_id),
  CONSTRAINT fk_settlement_order FOREIGN KEY (order_id) REFERENCES service_order(id),
  CONSTRAINT ck_settlement_amount CHECK (amount >= 0),
  CONSTRAINT ck_settlement_method CHECK (method IS NULL OR method IN ('SIMULATED','OFFLINE','WAIVED')),
  CONSTRAINT ck_settlement_status CHECK (status IN ('WAITING_METHOD','PROCESSING','SETTLED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_attempt (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  settlement_id BIGINT NOT NULL,
  attempt_ref VARCHAR(64) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  settlement_version INT NOT NULL,
  remote_status VARCHAR(24) NOT NULL DEFAULT 'UNREGISTERED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  synced_at DATETIME(3) NULL,
  UNIQUE KEY uk_payment_ref (attempt_ref),
  INDEX ix_payment_settlement (settlement_id,id),
  CONSTRAINT fk_attempt_settlement FOREIGN KEY (settlement_id) REFERENCES settlement(id),
  CONSTRAINT ck_attempt_status CHECK (remote_status IN ('UNREGISTERED','PROCESSING','SUCCESS','FAILED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS settlement_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  settlement_id BIGINT NOT NULL,
  actor_id BIGINT NOT NULL,
  action VARCHAR(40) NOT NULL,
  note VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_settlement_event_settlement FOREIGN KEY (settlement_id) REFERENCES settlement(id),
  CONSTRAINT fk_settlement_event_actor FOREIGN KEY (actor_id) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
