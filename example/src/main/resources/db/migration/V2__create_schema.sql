-- USERS
CREATE TABLE "users"(
    "id" BIGINT NOT NULL,
    "login" VARCHAR NOT NULL,
    "login_lowercase" VARCHAR NOT NULL,
    "email" VARCHAR NOT NULL,
    "password" VARCHAR NOT NULL,
    "salt" VARCHAR NOT NULL,
    "last_login" TIMESTAMP,
    PRIMARY KEY("id")
);
CREATE UNIQUE INDEX "users_login_lowercase" ON "users"("login_lowercase");
CREATE UNIQUE INDEX "users_email" ON "users"("email");

-- APIKEYS
CREATE TABLE "apikeys"(
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "apikey" VARCHAR NOT NULL,
  "created" TIMESTAMP NOT NULL,
  PRIMARY KEY("id")
);
ALTER TABLE "apikeys" ADD CONSTRAINT "apikeys_user_fk"
  FOREIGN KEY("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
CREATE UNIQUE INDEX "apikeys_apikey" ON "apikeys"("apikey");