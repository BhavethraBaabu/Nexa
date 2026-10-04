-- Runs once when the local Postgres container is first created.
CREATE DATABASE nexa_test OWNER nexa;
\connect nexa
CREATE EXTENSION IF NOT EXISTS vector;
\connect nexa_test
CREATE EXTENSION IF NOT EXISTS vector;
