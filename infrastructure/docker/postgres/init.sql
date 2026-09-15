-- Runs once, on first container start, against POSTGRES_DB.
CREATE EXTENSION IF NOT EXISTS vector;

-- order-service owns its own schema within the shared local Postgres instance (a POC
-- simplification vs. database-per-service — see docs/architecture.md).
CREATE SCHEMA IF NOT EXISTS order_service;

-- Reserved for the RAG pipeline (rca-platform phase): document chunks + embeddings.
CREATE SCHEMA IF NOT EXISTS rag;
