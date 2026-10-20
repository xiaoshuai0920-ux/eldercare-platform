CREATE TABLE IF NOT EXISTS service_incident (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  reported_by BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  resolved_by BIGINT NULL,
  resolution VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  resolved_at DATETIME(3) NULL,
  INDEX ix_incident_order (order_id,status),
  CONSTRAINT fk_incident_order FOREIGN KEY (order_id) REFERENCES service_order(id),
  CONSTRAINT fk_incident_reporter FOREIGN KEY (reported_by) REFERENCES account(id),
  CONSTRAINT fk_incident_resolver FOREIGN KEY (resolved_by) REFERENCES account(id),
  CONSTRAINT ck_incident_status CHECK (status IN ('OPEN','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS service_review (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  author_id BIGINT NOT NULL,
  stars TINYINT NOT NULL,
  content VARCHAR(1000) NULL,
  visibility VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
  moderated_by BIGINT NULL,
  moderation_reason VARCHAR(300) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  moderated_at DATETIME(3) NULL,
  UNIQUE KEY uk_review_order (order_id),
  CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES service_order(id),
  CONSTRAINT fk_review_author FOREIGN KEY (author_id) REFERENCES account(id),
  CONSTRAINT fk_review_moderator FOREIGN KEY (moderated_by) REFERENCES account(id),
  CONSTRAINT ck_review_stars CHECK (stars BETWEEN 1 AND 5),
  CONSTRAINT ck_review_visibility CHECK (visibility IN ('VISIBLE','HIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_dispute (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  opened_by BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  resolved_by BIGINT NULL,
  resolution VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  resolved_at DATETIME(3) NULL,
  INDEX ix_dispute_order (order_id,status),
  CONSTRAINT fk_dispute_order FOREIGN KEY (order_id) REFERENCES service_order(id),
  CONSTRAINT fk_dispute_opener FOREIGN KEY (opened_by) REFERENCES account(id),
  CONSTRAINT fk_dispute_resolver FOREIGN KEY (resolved_by) REFERENCES account(id),
  CONSTRAINT ck_dispute_status CHECK (status IN ('OPEN','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fee_adjustment (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  proposed_by BIGINT NOT NULL,
  original_amount DECIMAL(12,2) NOT NULL,
  proposed_amount DECIMAL(12,2) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  decided_by BIGINT NULL,
  decided_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX ix_fee_order (order_id,status),
  CONSTRAINT fk_fee_order FOREIGN KEY (order_id) REFERENCES service_order(id),
  CONSTRAINT fk_fee_proposer FOREIGN KEY (proposed_by) REFERENCES account(id),
  CONSTRAINT fk_fee_decider FOREIGN KEY (decided_by) REFERENCES account(id),
  CONSTRAINT ck_fee_amount CHECK (original_amount >= 0 AND proposed_amount >= 0),
  CONSTRAINT ck_fee_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS supplement_plan (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  created_by BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  start_at DATETIME(3) NOT NULL,
  end_at DATETIME(3) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
  active_order_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('PLANNED','ASSIGNED','IN_SERVICE','SUBMITTED') THEN order_id ELSE NULL END) STORED,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_active_supplement (active_order_id),
  INDEX ix_supplement_order (order_id,id),
  CONSTRAINT fk_supplement_order FOREIGN KEY (order_id) REFERENCES service_order(id),
  CONSTRAINT fk_supplement_creator FOREIGN KEY (created_by) REFERENCES account(id),
  CONSTRAINT ck_supplement_time CHECK (start_at < end_at),
  CONSTRAINT ck_supplement_status CHECK (status IN ('PLANNED','ASSIGNED','IN_SERVICE','SUBMITTED','CONFIRMED','INTERRUPTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
