
## Patient consultations and documents (mobile 18.6)

Authenticated PATIENT routes resolve the patient strictly from JWT.sub: `GET /api/v1/patients/me/consultations`, `/consultations/{id}`, `/documents` (optional `consultationId`), `/documents/{id}` and `/documents/{id}/content`. Only completed consultations and explicitly published notes/diagnoses are returned; only ACTIVE explicitly published own documents can be read. The binary route streams authenticated content with `no-store`, `nosniff` and attachment disposition; patient metadata excludes storage keys and URLs.

Migration V16 adds `patient_visible=false` to existing notes, diagnoses and documents. Professionals with existing write access can publish/revoke using `PATCH /api/v1/consultations/{id}/notes/{noteId}/patient-visibility`, `/diagnoses/{diagnosisId}/patient-visibility`, and `/api/v1/documents/{id}/patient-visibility`, with `{"patientVisible":true}` or false. Creation accepts an optional flag, false by default. Changes are locked and audited. Existing content is not automatically published. Legacy document routes enforce the same patient ownership and publication checks.

## Laboratory and prescriptions (mobile 18.7)

`GET /api/v1/patients/me/lab-results` and `/lab-results/{id}` expose only FINAL results with a validator and validation timestamp. Patient DTOs contain exam/parameter names, values, units, reference limits and recorded interpretations/flags; draft results, internal notes, specimen details and order instructions are excluded.

`GET /api/v1/patients/me/prescriptions` and `/prescriptions/{id}` provide named prescriber/organization, medication and dosing details, prescribed/dispensed/remaining quantities and dispensing history with pharmacy names. The expired flag applies to ACTIVE/PARTIALLY_DISPENSED prescriptions past their expiration; terminal statuses remain unchanged. Both lists use page/size (default 0/20, maximum 100).

Every route requires PATIENT and resolves ownership strictly from the Keycloak subject through PersonLookup. Reads are audited. Legacy patient laboratory/prescription reads enforce the same ownership restriction; patient laboratory responses exclude draft results and internal notes. No patient mutation is added.
