-- Roamly Database Initialization Script
-- This script creates the database and extensions. 
-- The tables and indexes are managed by Flyway migrations.

-- Create extension for UUID generation if not exists
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Note: The database 'roamly' is created by the environment variables in docker-compose.
-- This script runs inside that database context.
