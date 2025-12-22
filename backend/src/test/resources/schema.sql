CREATE TABLE users (
  id INTEGER NOT NULL,
  email VARCHAR NOT NULL,
  username VARCHAR NULL,
  role VARCHAR NOT NULL,
  is_active BOOLEAN NOT NULL,
  created_at TIMESTAMP NULL,
  updated_at TIMESTAMP NULL,
  CONSTRAINT users_pkey PRIMARY KEY (id),
  CONSTRAINT uq_users_email UNIQUE (email)
);
