CREATE TABLE IF NOT EXISTS outbox_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_event_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  processed_at DATETIME(3) NULL,
  last_error VARCHAR(300) NULL,
  UNIQUE KEY uk_outbox_order_event (order_event_id),
  INDEX ix_outbox_due (status,next_attempt_at),
  CONSTRAINT fk_outbox_order_event FOREIGN KEY (order_event_id) REFERENCES order_event(id),
  CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','DONE','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS life_reminder (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  elder_id BIGINT NOT NULL,
  created_by BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  recurrence VARCHAR(16) NOT NULL,
  next_fire_at DATETIME(3) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX ix_reminder_due (status,next_fire_at),
  CONSTRAINT fk_reminder_elder FOREIGN KEY (elder_id) REFERENCES elder_profile(id),
  CONSTRAINT fk_reminder_creator FOREIGN KEY (created_by) REFERENCES account(id),
  CONSTRAINT ck_reminder_recurrence CHECK (recurrence IN ('ONCE','DAILY','WEEKLY')),
  CONSTRAINT ck_reminder_status CHECK (status IN ('ACTIVE','STOPPED','FINISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notification (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  order_event_id BIGINT NULL,
  reminder_id BIGINT NULL,
  reminder_due_at DATETIME(3) NULL,
  title VARCHAR(100) NOT NULL,
  body VARCHAR(500) NOT NULL,
  target_path VARCHAR(100) NOT NULL,
  priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  read_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_order_notification (account_id,order_event_id),
  UNIQUE KEY uk_reminder_notification (account_id,reminder_id,reminder_due_at),
  INDEX ix_notification_account (account_id,read_at,id),
  CONSTRAINT fk_notification_account FOREIGN KEY (account_id) REFERENCES account(id),
  CONSTRAINT fk_notification_event FOREIGN KEY (order_event_id) REFERENCES order_event(id),
  CONSTRAINT fk_notification_reminder FOREIGN KEY (reminder_id) REFERENCES life_reminder(id),
  CONSTRAINT ck_notification_priority CHECK (priority IN ('NORMAL','ACTION','HIGH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reminder_delivery (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  reminder_id BIGINT NOT NULL,
  due_at DATETIME(3) NOT NULL,
  account_id BIGINT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_reminder_delivery (reminder_id,due_at,account_id),
  CONSTRAINT fk_delivery_reminder FOREIGN KEY (reminder_id) REFERENCES life_reminder(id),
  CONSTRAINT fk_delivery_account FOREIGN KEY (account_id) REFERENCES account(id),
  CONSTRAINT ck_delivery_status CHECK (status IN ('DELIVERED','EXPIRED','NO_RECIPIENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

