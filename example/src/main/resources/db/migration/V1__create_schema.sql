-- USERS
CREATE TABLE "users"(
    "id" BIGINT NOT NULL,
    "login" VARCHAR NOT NULL,
    "login_lowercase" VARCHAR NOT NULL,
    "email" VARCHAR NOT NULL,
    "password" VARCHAR NOT NULL,
    "salt" VARCHAR NOT NULL,
    "last_login" TIMESTAMP
);
ALTER TABLE "users" ADD CONSTRAINT "users_id" PRIMARY KEY("id");
CREATE UNIQUE INDEX "users_login_lowercase" ON "users"("login_lowercase");
CREATE UNIQUE INDEX "users_email" ON "users"("email");

-- APIKEYS
CREATE TABLE "apikeys"(
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "apikey" VARCHAR NOT NULL,
  "created" TIMESTAMP NOT NULL
);
ALTER TABLE "apikeys" ADD CONSTRAINT "apikeys_id" PRIMARY KEY("id");
ALTER TABLE "apikeys" ADD CONSTRAINT "apikeys_user_fk"
  FOREIGN KEY("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
CREATE UNIQUE INDEX "apikeys_apikey" ON "apikeys"("apikey");

-- EVENTS
CREATE TABLE "events"(
    "id" BIGINT NOT NULL,
    "event_type" VARCHAR NOT NULL,
    "aggregate_type" VARCHAR NOT NULL,
    "aggregate_id" BIGINT NOT NULL,
    "aggregate_is_new" BOOLEAN NOT NULL,
    "created" TIMESTAMP NOT NULL,
    "user_id" BIGINT NOT NULL,
    "tx_id" BIGINT NOT NULL,
    "event_json" VARCHAR NOT NULL
);
ALTER TABLE "events" ADD CONSTRAINT "events_id" PRIMARY KEY("id");