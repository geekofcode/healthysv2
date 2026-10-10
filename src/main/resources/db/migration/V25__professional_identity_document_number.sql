-- Keep historic dossiers intact; a complete number is required for any new submission/approval.
ALTER TABLE professional.registration_request ADD COLUMN identity_document_number varchar(100);
