
## Patient consultations and documents (mobile 18.6)

Authenticated PATIENT routes resolve the patient strictly from JWT.sub: `GET /api/v1/patients/me/consultations`, `/consultations/{id}`, `/documents` (optional `consultationId`), `/documents/{id}` and `/documents/{id}/content`. Only completed consultations and explicitly published notes/diagnoses are returned; only ACTIVE explicitly published own documents can be read. The binary route streams authenticated content with `no-store`, `nosniff` and attachment disposition; patient metadata excludes storage keys and URLs.

Migration V16 adds `patient_visible=false` to existing notes, diagnoses and documents. Professionals with existing write access can publish/revoke using `PATCH /api/v1/consultations/{id}/notes/{noteId}/patient-visibility`, `/diagnoses/{diagnosisId}/patient-visibility`, and `/api/v1/documents/{id}/patient-visibility`, with `{"patientVisible":true}` or false. Creation accepts an optional flag, false by default. Changes are locked and audited. Existing content is not automatically published. Legacy document routes enforce the same patient ownership and publication checks.
