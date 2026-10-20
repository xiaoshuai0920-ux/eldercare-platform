CREATE TABLE IF NOT EXISTS service_item_tag (
  item_id BIGINT NOT NULL,
  tag VARCHAR(60) NOT NULL,
  PRIMARY KEY (item_id,tag),
  CONSTRAINT fk_item_tag_item FOREIGN KEY (item_id) REFERENCES service_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS elder_preference_tag (
  elder_id BIGINT NOT NULL,
  tag VARCHAR(60) NOT NULL,
  PRIMARY KEY (elder_id,tag),
  CONSTRAINT fk_preference_elder FOREIGN KEY (elder_id) REFERENCES elder_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
