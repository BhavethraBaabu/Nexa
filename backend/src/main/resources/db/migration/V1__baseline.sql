-- Nexa baseline migration.
-- Phase 0 only establishes Flyway history. Domain tables (organizations, users,
-- meetings, ...) are introduced by later phases as described in PRD section 28.
-- gen_random_uuid() is built into PostgreSQL 13+, so no extension is required for UUID keys.

COMMENT ON SCHEMA public IS 'Nexa application schema';
