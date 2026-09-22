-- LiveKit-backed teleconsultation workflow invariants.
ALTER TABLE teleconsultation.video_session
    ADD CONSTRAINT ck_video_session_provider CHECK (provider IN ('LIVEKIT')),
    ADD CONSTRAINT ck_video_session_status CHECK (status IN ('SCHEDULED','WAITING','ACTIVE','COMPLETED','CANCELLED')),
    ADD CONSTRAINT ck_video_session_source CHECK (appointment_id IS NOT NULL OR consultation_id IS NOT NULL),
    ADD CONSTRAINT ck_video_session_times CHECK (ended_at IS NULL OR started_at IS NULL OR ended_at >= started_at);

ALTER TABLE teleconsultation.video_participant
    ADD CONSTRAINT uq_video_participant UNIQUE (video_session_id, person_id),
    ADD CONSTRAINT ck_video_participant_role CHECK (role IN ('PATIENT','PROFESSIONAL','HOST','OBSERVER')),
    ADD CONSTRAINT ck_video_participant_times CHECK (left_at IS NULL OR joined_at IS NULL OR left_at >= joined_at);

ALTER TABLE teleconsultation.waiting_room
    ADD CONSTRAINT uq_waiting_room_patient UNIQUE (video_session_id, patient_id),
    ADD CONSTRAINT ck_waiting_room_status CHECK (status IN ('WAITING','ADMITTED','LEFT','REJECTED')),
    ADD CONSTRAINT ck_waiting_room_times CHECK (admitted_at IS NULL OR admitted_at >= entered_at);

CREATE INDEX idx_video_participant_person ON teleconsultation.video_participant(person_id);
CREATE INDEX idx_waiting_room_session_status ON teleconsultation.waiting_room(video_session_id, status);
