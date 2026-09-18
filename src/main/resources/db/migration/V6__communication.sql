-- HEALTH'YS PostgreSQL / Flyway migration
-- UUID technical identifiers; Keycloak manages authentication separately.

CREATE SCHEMA IF NOT EXISTS communication;
CREATE SCHEMA IF NOT EXISTS teleconsultation;
CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE communication.conversation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_number VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL,
    subject VARCHAR(500),
    patient_id UUID REFERENCES patient.patient(id),
    created_by UUID NOT NULL REFERENCES identity.person(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE communication.conversation_participant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES communication.conversation(id),
    person_id UUID NOT NULL REFERENCES identity.person(id),
    participant_role VARCHAR(50),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_conversation_participant UNIQUE(conversation_id, person_id)
);

CREATE TABLE communication.message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES communication.conversation(id),
    sender_person_id UUID NOT NULL REFERENCES identity.person(id),
    type VARCHAR(30) NOT NULL DEFAULT 'TEXT',
    content TEXT,
    reply_to_message_id UUID REFERENCES communication.message(id),
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_message_conversation_time ON communication.message(conversation_id, sent_at);

CREATE TABLE communication.message_attachment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES communication.message(id),
    document_id UUID NOT NULL REFERENCES document.document(id),
    CONSTRAINT uq_message_document UNIQUE(message_id, document_id)
);

CREATE TABLE communication.message_receipt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES communication.message(id),
    person_id UUID NOT NULL REFERENCES identity.person(id),
    delivered_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    CONSTRAINT uq_message_receipt UNIQUE(message_id, person_id)
);

CREATE TABLE communication.message_reaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES communication.message(id),
    person_id UUID NOT NULL REFERENCES identity.person(id),
    reaction VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_reaction UNIQUE(message_id, person_id, reaction)
);

CREATE TABLE teleconsultation.video_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_number VARCHAR(50) NOT NULL UNIQUE,
    appointment_id UUID REFERENCES appointment.appointment(id),
    consultation_id UUID REFERENCES consultation.consultation(id),
    provider VARCHAR(50) NOT NULL,
    external_room_id VARCHAR(255) NOT NULL UNIQUE,
    scheduled_start TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED'
);

CREATE TABLE teleconsultation.video_participant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_session_id UUID NOT NULL REFERENCES teleconsultation.video_session(id),
    person_id UUID NOT NULL REFERENCES identity.person(id),
    role VARCHAR(50) NOT NULL,
    joined_at TIMESTAMPTZ,
    left_at TIMESTAMPTZ
);

CREATE TABLE teleconsultation.waiting_room (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_session_id UUID NOT NULL REFERENCES teleconsultation.video_session(id),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    entered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    admitted_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'WAITING'
);

CREATE TABLE teleconsultation.video_recording (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_session_id UUID NOT NULL REFERENCES teleconsultation.video_session(id),
    document_id UUID NOT NULL REFERENCES document.document(id),
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    consent_id UUID REFERENCES patient.consent(id),
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED'
);

CREATE TABLE notification.notification_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    channel VARCHAR(30) NOT NULL,
    subject_template TEXT,
    body_template TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE notification.notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(80) NOT NULL,
    title VARCHAR(255),
    body TEXT NOT NULL,
    resource_type VARCHAR(50),
    resource_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED'
);

CREATE TABLE notification.notification_recipient (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL REFERENCES notification.notification(id),
    person_id UUID NOT NULL REFERENCES identity.person(id),
    read_at TIMESTAMPTZ,
    CONSTRAINT uq_notification_recipient UNIQUE(notification_id, person_id)
);

CREATE TABLE notification.notification_delivery (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL REFERENCES notification.notification(id),
    person_id UUID NOT NULL REFERENCES identity.person(id),
    channel VARCHAR(30) NOT NULL,
    destination VARCHAR(500),
    provider VARCHAR(100),
    provider_message_id VARCHAR(255),
    sent_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    error_message TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
);

-- FK/query indexes
CREATE INDEX IF NOT EXISTS idx_conversation_patient ON communication.conversation(patient_id);
CREATE INDEX IF NOT EXISTS idx_conversation_participant_person ON communication.conversation_participant(person_id);
CREATE INDEX IF NOT EXISTS idx_message_sender ON communication.message(sender_person_id);
CREATE INDEX IF NOT EXISTS idx_video_session_appointment ON teleconsultation.video_session(appointment_id);
CREATE INDEX IF NOT EXISTS idx_video_session_consultation ON teleconsultation.video_session(consultation_id);
CREATE INDEX IF NOT EXISTS idx_notification_recipient_person ON notification.notification_recipient(person_id);
CREATE INDEX IF NOT EXISTS idx_notification_delivery_person ON notification.notification_delivery(person_id);
