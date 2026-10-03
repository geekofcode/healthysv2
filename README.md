
## Patient consultations and documents (mobile 18.6)

Authenticated PATIENT routes resolve the patient strictly from JWT.sub: `GET /api/v1/patients/me/consultations`, `/consultations/{id}`, `/documents` (optional `consultationId`), `/documents/{id}` and `/documents/{id}/content`. Only completed consultations and explicitly published notes/diagnoses are returned; only ACTIVE explicitly published own documents can be read. The binary route streams authenticated content with `no-store`, `nosniff` and attachment disposition; patient metadata excludes storage keys and URLs.

Migration V16 adds `patient_visible=false` to existing notes, diagnoses and documents. Professionals with existing write access can publish/revoke using `PATCH /api/v1/consultations/{id}/notes/{noteId}/patient-visibility`, `/diagnoses/{diagnosisId}/patient-visibility`, and `/api/v1/documents/{id}/patient-visibility`, with `{"patientVisible":true}` or false. Creation accepts an optional flag, false by default. Changes are locked and audited. Existing content is not automatically published. Legacy document routes enforce the same patient ownership and publication checks.

## Laboratory and prescriptions (mobile 18.7)

`GET /api/v1/patients/me/lab-results` and `/lab-results/{id}` expose only FINAL results with a validator and validation timestamp. Patient DTOs contain exam/parameter names, values, units, reference limits and recorded interpretations/flags; draft results, internal notes, specimen details and order instructions are excluded.

`GET /api/v1/patients/me/prescriptions` and `/prescriptions/{id}` provide named prescriber/organization, medication and dosing details, prescribed/dispensed/remaining quantities and dispensing history with pharmacy names. The expired flag applies to ACTIVE/PARTIALLY_DISPENSED prescriptions past their expiration; terminal statuses remain unchanged. Both lists use page/size (default 0/20, maximum 100).

Every route requires PATIENT and resolves ownership strictly from the Keycloak subject through PersonLookup. Reads are audited. Legacy patient laboratory/prescription reads enforce the same ownership restriction; patient laboratory responses exclude draft results and internal notes. No patient mutation is added.

## Maternal-child notebook (mobile 18.8)

Patient self routes `GET /api/v1/patients/me/maternal-child/pregnancies`, `/pregnancies/{id}`, `/children` and `/children/{childPatientId}` expose pregnancies, prenatal measurements, birth, vaccinations and growth. Both lists are paginated (default page 0, size 20, maximum size 100). DTO fields carry explicit kg/cm units. Internal prenatal/delivery/risk/postpartum notes and clinical comments are excluded.

The Keycloak linkage resolves the current patient strictly. A mother can read her own pregnancies and children linked by the existing child-health-record mother relationship; a child can read only their own child notebook, without maternal pregnancy data or siblings. No automatic relationship or generalized guardianship permission is introduced. Patient reads are audited; legacy patient endpoints apply the same ownership and privacy restrictions while professional care operations retain their existing access rules.

## Mobile realtime messaging (18.9)

Conversation and message REST routes support paginated history, authenticated sends and read receipts. The mobile client treats STOMP events as invalidations and reloads REST data after reconnection, rather than assuming broadcast payloads have recipient-specific read state. Mutations are not automatically replayed after network or authentication failures.

STOMP 1.2 connects at `/ws`; deployments must preserve the WebSocket upgrade and route the API and socket through the same trusted origin. JWT credentials belong in the CONNECT Authorization header, never in query parameters. Subscription and send destinations are explicitly authorized. Conversation attachments use authenticated metadata/content routes, bounded uploads and the document storage validation rules; storage keys and public URLs are not returned.

`GET /api/v1/conversations/recipients` returns the connected patient's active care-team professionals as named recipients. Patient conversation creation validates that selection and rejects another patient identifier or recipients outside that care team. This adds no unrestricted person directory.

Conversation attachments use `POST /api/v1/conversations/{id}/attachments` (multipart `file`), `GET /{documentId}` and `GET /{documentId}/content`. Upload responses contain only id, filename, MIME type, byte size, timestamp and status. An uploaded draft is readable by its uploader; recipients gain access after it is attached to a message in that conversation. Sharing an attachment does not publish it into the patient's medical dossier. Messages expose `readByOthersCount` independently of `readByCurrentUser`, so a sender's own receipt is not presented as a recipient read.

## Mobile push notifications (18.10)

Migration V17 adds device registrations and a transactional push outbox. Existing staff notification creation queues push for active devices whose recipient has enabled push. `GET /api/v1/notifications/{id}` resolves an unexpired notification only for its recipient. Notification and device identity use the strict Keycloak subject linkage.

`PUT /api/v1/notifications/devices/{installationId}` accepts `{token,platform,revocationToken}` (`ANDROID`/`IOS`). The installation UUID and a random 32-byte base64url revocation capability are generated and saved securely by the client before registration. Tokens are unique across installations; only the owner can rotate or unregister an active device. `DELETE /{installationId}` requires the owner JWT. `POST /api/v1/notifications/devices/revoke` accepts `{installationId,revocationToken}` without a JWT, allowing cleanup after session expiry; it reveals no registration state and can only revoke the matching capability. The server stores a hash of this capability. Never log device tokens or request bodies.

Push is disabled by default. To activate it, set `PUSH_ENABLED=true`, `FCM_PROJECT_ID` and `FCM_SERVICE_ACCOUNT_FILE` to an absolute path to a mounted service-account secret. Grant the service account Firebase Cloud Messaging sending permission and enable the FCM HTTP v1 API. The credential file belongs outside source control and container images. Configure the Apple APNs authentication key and matching bundle ID in Firebase for iOS delivery. No credentials or provider accounts are provisioned by this change.

FCM payloads contain a generic localized HEALTH’YS alert and only `notificationId` as application data. Medical content, person identifiers and destination URLs are excluded. Android uses channel `healthys_notifications`; APNs uses alert delivery. FCM acceptance is recorded as SENT, not as proof of device delivery. The worker rechecks recipient preference, read state, expiration and device ownership before sending, retries transient failures up to five attempts, and disables unregistered tokens. The outbox is bounded to 10,000 pending entries; overflow is recorded as a failed delivery. Completed outbox entries are removed after 30 days.

Preferences retain the existing email/SMS fields. Quiet hours are explicitly UTC and defer push until the end of the interval; equal start/end times are invalid. Disabling push stops queued sends. Email/SMS provider delivery is outside this mobile push implementation. Device credentials require the same database protection as other application secrets. Automated tests use a fake push gateway; Firebase/APNs reception must be validated on configured physical devices.
