CREATE TABLE "events"(
    "id" BIGINT NOT NULL,
    "event_type" VARCHAR NOT NULL,
    "aggregate_type" VARCHAR NOT NULL,
    "aggregate_id" BIGINT NOT NULL,
    "aggregate_is_new" BOOLEAN NOT NULL,
    "created" TIMESTAMP NOT NULL,
    "user_id" BIGINT NOT NULL,
    "tx_id" BIGINT NOT NULL,
    "event_json" VARCHAR NOT NULL,
    PRIMARY KEY("id")
);
