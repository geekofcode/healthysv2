ALTER TABLE communication.conversation
    ADD CONSTRAINT ck_conversation_type CHECK (type IN ('DIRECT', 'CARE_TEAM', 'PATIENT_SUPPORT', 'GROUP')),
    ADD CONSTRAINT ck_conversation_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'CLOSED'));

ALTER TABLE communication.conversation_participant
    ADD CONSTRAINT ck_conversation_participant_status CHECK (status IN ('ACTIVE', 'LEFT'));

ALTER TABLE communication.message
    ADD CONSTRAINT ck_message_type CHECK (type IN ('TEXT', 'DOCUMENT', 'SYSTEM')),
    ADD CONSTRAINT ck_message_content CHECK (content IS NOT NULL OR type = 'DOCUMENT');

CREATE INDEX idx_conversation_created_at ON communication.conversation(created_at DESC);
CREATE INDEX idx_conversation_participant_active
    ON communication.conversation_participant(person_id, conversation_id)
    WHERE status = 'ACTIVE' AND left_at IS NULL;
CREATE INDEX idx_message_receipt_person ON communication.message_receipt(person_id, read_at);

