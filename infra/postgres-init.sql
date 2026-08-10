-- Creates the two per-service databases and enables pgvector where ResumeService needs it.
-- Runs once on first container start (mounted into /docker-entrypoint-initdb.d).
CREATE DATABASE resume;
CREATE DATABASE search;

\connect resume
CREATE EXTENSION IF NOT EXISTS vector;

\connect search
CREATE EXTENSION IF NOT EXISTS vector;
