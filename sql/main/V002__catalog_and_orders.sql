CREATE TABLE IF NOT EXISTS service_area (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT NULL,
  level VARCHAR(12) NOT NULL,
  name VARCHAR(80) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_area_parent_name (parent_id,name),
  CONSTRAINT fk_area_parent FOREIGN KEY (parent_id) REFERENCES service_area(id),
  CONSTRAINT ck_area_level CHECK (level IN ('CITY','DISTRICT','STREET'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS service_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  category VARCHAR(16) NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  pricing_model VARCHAR(16) NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  min_minutes INT NOT NULL,
  max_minutes INT NOT NULL,
  available_from TIME NOT NULL,
  available_to TIME NOT NULL,
  advance_minutes INT NOT NULL DEFAULT 120,
  rule_text VARCHAR(500) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT ck_item_category CHECK (category IN ('MEAL','CLEANING','ESCORT')),
  CONSTRAINT ck_pricing_model CHECK (pricing_model IN ('FIXED','HOURLY')),
  CONSTRAINT ck_item_price CHECK (unit_price >= 0),
  CONSTRAINT ck_item_duration CHECK (min_minutes > 0 AND max_minutes >= min_minutes)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS service_item_area (
  item_id BIGINT NOT NULL,
  street_id BIGINT NOT NULL,
  PRIMARY KEY (item_id,street_id),
  CONSTRAINT fk_item_area_item FOREIGN KEY (item_id) REFERENCES service_item(id),
  CONSTRAINT fk_item_area_street FOREIGN KEY (street_id) REFERENCES service_area(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS worker_capability (
  worker_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  street_id BIGINT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (worker_id,item_id,street_id),
  CONSTRAINT fk_cap_worker FOREIGN KEY (worker_id) REFERENCES account(id),
  CONSTRAINT fk_cap_item FOREIGN KEY (item_id) REFERENCES service_item(id),
  CONSTRAINT fk_cap_street FOREIGN KEY (street_id) REFERENCES service_area(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS worker_schedule (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  worker_id BIGINT NOT NULL,
  weekday TINYINT NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  CONSTRAINT fk_schedule_worker FOREIGN KEY (worker_id) REFERENCES account(id),
  CONSTRAINT ck_weekday CHECK (weekday BETWEEN 1 AND 7),
  CONSTRAINT ck_schedule_time CHECK (start_time < end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS worker_leave (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  worker_id BIGINT NOT NULL,
  start_at DATETIME(3) NOT NULL,
  end_at DATETIME(3) NOT NULL,
  reason VARCHAR(300) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  decided_by BIGINT NULL,
  decided_at DATETIME(3) NULL,
  CONSTRAINT fk_leave_worker FOREIGN KEY (worker_id) REFERENCES account(id),
  CONSTRAINT fk_leave_decider FOREIGN KEY (decided_by) REFERENCES account(id),
  CONSTRAINT ck_leave_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
  CONSTRAINT ck_leave_times CHECK (start_at < end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS service_order (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  creator_id BIGINT NOT NULL,
  elder_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  item_name_snapshot VARCHAR(100) NOT NULL,
  category_snapshot VARCHAR(16) NOT NULL,
  rule_snapshot VARCHAR(500) NOT NULL,
  pricing_model_snapshot VARCHAR(16) NOT NULL,
  unit_price_snapshot DECIMAL(12,2) NOT NULL,
  duration_minutes INT NOT NULL,
  original_amount DECIMAL(12,2) NOT NULL,
  payable_amount DECIMAL(12,2) NOT NULL,
  street_id BIGINT NOT NULL,
  street_name_snapshot VARCHAR(240) NOT NULL,
  address_snapshot VARCHAR(300) NOT NULL,
  start_at DATETIME(3) NOT NULL,
  end_at DATETIME(3) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'WAITING_ASSIGNMENT',
  version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX ix_order_elder_status (elder_id,status,created_at),
  INDEX ix_order_start (start_at,status),
  CONSTRAINT fk_order_creator FOREIGN KEY (creator_id) REFERENCES account(id),
  CONSTRAINT fk_order_elder FOREIGN KEY (elder_id) REFERENCES elder_profile(id),
  CONSTRAINT fk_order_item FOREIGN KEY (item_id) REFERENCES service_item(id),
  CONSTRAINT fk_order_street FOREIGN KEY (street_id) REFERENCES service_area(id),
  CONSTRAINT ck_order_amount CHECK (original_amount >= 0 AND payable_amount >= 0),
  CONSTRAINT ck_order_time CHECK (start_at < end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS assignment (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  worker_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  active_order_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING','ACCEPTED') THEN order_id ELSE NULL END) STORED,
  assigned_by BIGINT NOT NULL,
  assigned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  responded_at DATETIME(3) NULL,
  rejection_reason VARCHAR(300) NULL,
  UNIQUE KEY uk_active_assignment (active_order_id),
  INDEX ix_assignment_worker_status (worker_id,status),
  CONSTRAINT fk_assignment_order FOREIGN KEY (order_id) REFERENCES service_order(id),
  CONSTRAINT fk_assignment_worker FOREIGN KEY (worker_id) REFERENCES account(id),
  CONSTRAINT fk_assignment_admin FOREIGN KEY (assigned_by) REFERENCES account(id),
  CONSTRAINT ck_assignment_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED','REPLACED','RELEASED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS worker_occupancy (
  assignment_id BIGINT NOT NULL PRIMARY KEY,
  worker_id BIGINT NOT NULL,
  start_at DATETIME(3) NOT NULL,
  end_at DATETIME(3) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  INDEX ix_occupancy_worker_time (worker_id,active,start_at,end_at),
  CONSTRAINT fk_occupancy_assignment FOREIGN KEY (assignment_id) REFERENCES assignment(id),
  CONSTRAINT fk_occupancy_worker FOREIGN KEY (worker_id) REFERENCES account(id),
  CONSTRAINT ck_occupancy_time CHECK (start_at < end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fulfillment (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  assignment_id BIGINT NOT NULL,
  started_at DATETIME(3) NULL,
  submitted_at DATETIME(3) NULL,
  confirmed_at DATETIME(3) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  INDEX ix_fulfillment_order (order_id),
  CONSTRAINT fk_fulfillment_order FOREIGN KEY (order_id) REFERENCES service_order(id),
  CONSTRAINT fk_fulfillment_assignment FOREIGN KEY (assignment_id) REFERENCES assignment(id),
  CONSTRAINT ck_fulfillment_status CHECK (status IN ('PENDING','IN_PROGRESS','SUBMITTED','CONFIRMED','INTERRUPTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  actor_id BIGINT NOT NULL,
  action VARCHAR(50) NOT NULL,
  old_status VARCHAR(24) NULL,
  new_status VARCHAR(24) NOT NULL,
  note VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX ix_order_event_order (order_id,created_at),
  CONSTRAINT fk_order_event_order FOREIGN KEY (order_id) REFERENCES service_order(id),
  CONSTRAINT fk_order_event_actor FOREIGN KEY (actor_id) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS idempotency_record (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor_id BIGINT NOT NULL,
  command_name VARCHAR(60) NOT NULL,
  request_key VARCHAR(100) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  result_id BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_idempotency (actor_id,command_name,request_key),
  CONSTRAINT fk_idempotency_actor FOREIGN KEY (actor_id) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
