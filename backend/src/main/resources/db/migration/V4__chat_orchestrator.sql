-- V4: Chat orchestrator support
-- Adds tool call metadata and message type to workflow_messages

ALTER TABLE workflow_messages
    ADD COLUMN IF NOT EXISTS tool_calls_json jsonb;

ALTER TABLE workflow_messages
    ADD COLUMN IF NOT EXISTS message_type varchar(20) NOT NULL DEFAULT 'COMMAND';

CREATE INDEX IF NOT EXISTS idx_wf_messages_session_created
    ON workflow_messages(session_id, created_at);
