-- Jobs retain the selected membership source; never infer an actor's profile at execution.
ALTER TABLE care.ai_job ADD COLUMN created_profile_id UUID;
CREATE INDEX ai_job_actor_profile ON care.ai_job(actor_id,organization_id,created_profile_id,created_at DESC);
-- Historical queued jobs have no provable source profile and will be safely cancelled by the worker.
