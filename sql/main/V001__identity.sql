CREATE TABLE IF NOT EXISTS account (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(16) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  accepting BOOLEAN NOT NULL DEFAULT TRUE,
  failed_logins INT NOT NULL DEFAULT 0,
  locked_until DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_account_email (email),
  CONSTRAINT ck_account_role CHECK (role IN ('USER','WORKER','ADMIN')),
  CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE','PENDING_ACTIVATION','DISABLED','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS worker_activation (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  used_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_worker_activation_token (token_hash),
  CONSTRAINT fk_activation_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS email_verification (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  code_hash VARCHAR(100) NOT NULL,
  mode VARCHAR(16) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  consumed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX ix_email_verification_email (email, created_at),
  CONSTRAINT ck_verification_mode CHECK (mode IN ('DEMO','SMTP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS elder_profile (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  full_name VARCHAR(80) NOT NULL,
  birth_date DATE NULL,
  contact_note VARCHAR(200) NULL,
  created_by BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_elder_creator FOREIGN KEY (created_by) REFERENCES account(id),
  CONSTRAINT ck_elder_status CHECK (status IN ('ACTIVE','ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS family_grant (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  elder_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  can_view_service BOOLEAN NOT NULL DEFAULT FALSE,
  can_book BOOLEAN NOT NULL DEFAULT FALSE,
  can_manage_reminder BOOLEAN NOT NULL DEFAULT FALSE,
  can_cancel BOOLEAN NOT NULL DEFAULT FALSE,
  can_confirm BOOLEAN NOT NULL DEFAULT FALSE,
  can_review BOOLEAN NOT NULL DEFAULT FALSE,
  can_confirm_fee BOOLEAN NOT NULL DEFAULT FALSE,
  can_manage_family BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  granted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  revoked_at DATETIME(3) NULL,
  UNIQUE KEY uk_family_grant_elder_account (elder_id, account_id),
  INDEX ix_family_grant_account (account_id, status),
  CONSTRAINT fk_grant_elder FOREIGN KEY (elder_id) REFERENCES elder_profile(id),
  CONSTRAINT fk_grant_account FOREIGN KEY (account_id) REFERENCES account(id),
  CONSTRAINT ck_grant_status CHECK (status IN ('ACTIVE','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS family_invitation (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  elder_id BIGINT NOT NULL,
  inviter_id BIGINT NOT NULL,
  invitee_email VARCHAR(254) NOT NULL,
  can_view_service BOOLEAN NOT NULL DEFAULT FALSE,
  can_book BOOLEAN NOT NULL DEFAULT FALSE,
  can_manage_reminder BOOLEAN NOT NULL DEFAULT FALSE,
  can_cancel BOOLEAN NOT NULL DEFAULT FALSE,
  can_confirm BOOLEAN NOT NULL DEFAULT FALSE,
  can_review BOOLEAN NOT NULL DEFAULT FALSE,
  can_confirm_fee BOOLEAN NOT NULL DEFAULT FALSE,
  can_manage_family BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  expires_at DATETIME(3) NOT NULL,
  responded_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX ix_invitation_email (invitee_email, status),
  CONSTRAINT fk_invite_elder FOREIGN KEY (elder_id) REFERENCES elder_profile(id),
  CONSTRAINT fk_invite_inviter FOREIGN KEY (inviter_id) REFERENCES account(id),
  CONSTRAINT ck_invitation_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor_id BIGINT NULL,
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(50) NOT NULL,
  target_id BIGINT NULL,
  summary VARCHAR(500) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX ix_audit_target (target_type, target_id, created_at),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
