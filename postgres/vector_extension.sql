-- Enable the vector extension for pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- Grant necessary permissions
GRANT ALL PRIVILEGES ON DATABASE "study-buddy" TO daebecodin;
