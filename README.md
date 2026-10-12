
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

## Mobile teleconsultation (18.11)

The mobile waiting room uses additive `GET /api/v1/video-sessions/page` with the standard `{content,page}` pagination envelope. The existing list endpoint remains compatible with the web client. Participant names and an authoritative recipient-specific `canJoin` flag are additive. Patient responses contain only their own waiting-room entry and the assigned professionals. Every route resolves an active person strictly through JWT.sub; a person UUID fallback is forbidden.

Patients enter the waiting room explicitly. Only the assigned clinical professional or a permitted administrator can start/admit/complete. Patient join tokens require an ACTIVE session and their own ADMITTED entry; cancelled/no-show appointment sources are rejected. Entering an already admitted room preserves admission. Leaving marks only the caller and their waiting-room entry LEFT; it does not complete the clinician's session. Rejoining after leave requires admission again. LiveKit tokens already issued for an active room remain valid until their short expiry; local clients destroy them and disconnect on leave.

Set backend-only `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` and optionally `LIVEKIT_TOKEN_TTL_MINUTES` (default 5, allowed 1–15). Use WSS for deployed endpoints; insecure WS is restricted to loopback development. Configure TLS, RTC/TURN connectivity and the same server API credentials on LiveKit. The API secret never belongs in a mobile build.

**Required LiveKit server configuration:**

```yaml
room:
  auto_create: false
```

Rooms are explicitly created on authorized start and authorized token issuance, including after an empty-room timeout. Disabling automatic creation prevents still-valid old join tokens from recreating a room deleted after completion. This server setting is a deployment prerequisite, not a setting the application can enforce remotely.

Migration V18 adds durable room-cleanup jobs. Clinical completion commits first and denies further tokens. The worker then deletes the provider room, normally on the next 10-second poll; failures retry every 30 seconds up to 20 attempts with bounded HTTP timeouts. Failed jobs retain `failed_at` and a generic `last_error`, and log only the video-session identifier. Provider outages can delay actual remote disconnection. Operators should monitor pending/failed cleanup and, once the provider is healthy, retry a verified failed session:

```sql
UPDATE teleconsultation.room_cleanup
SET failed_at=NULL, attempts=0, next_attempt_at=now(), last_error=NULL
WHERE video_session_id='SESSION-UUID' AND completed_at IS NULL AND failed_at IS NOT NULL;
```

Completed jobs are purged after 30 days. Automated tests mock the LiveKit room gateway; admission, permissions, network interruption and media must also be checked with physical devices and a deployed LiveKit instance.

For the complete Firebase/APNs deployment procedure, see the [mobile configuration README](https://github.com/geekofcode/healthysv2M/blob/feature/18.1-flutter-foundation/docs/firebase-apns/README.md).


## Stabilisation mobile 18.12

Les tests API renforcent le retrait d’admission et la désactivation du compte pendant
une séance active, l’annulation du rendez-vous source, l’isolation des salles et
la sortie/réentrée sans nouvelle admission. Les tests unitaires LiveKit vérifient
la signature HMAC, l’identité, les droits limités à la salle, le TTL borné et
l’absence du bearer dans les diagnostics `JoinTokenResponse.toString()`.

La configuration de signature, les builds Android/iOS, les stores et la recette
physique sont documentés dans le
[guide de publication mobile](https://github.com/geekofcode/healthysv2M/blob/feature/18.1-flutter-foundation/docs/mobile-release/README.md).
Aucune publication store ni activation des fournisseurs n’est effectuée par la CI.
# Inscription et identité HEALTH’YS

Le [guide du thème Keycloak](docs/keycloak-theme.md) décrit l’installation du thème bleu, les champs d’inscription patient et les claims de synchronisation. Le [parcours professionnel](docs/professional-registration.md) décrit les demandes d’accès, la vérification administrative, les invitations et les affiliations facultatives aux établissements.
