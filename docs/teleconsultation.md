# LiveKit teleconsultation

HEALTH'YS stores the business lifecycle in PostgreSQL and uses LiveKit only for realtime media. A session is attached to exactly one appointment or consultation. The patient and assigned professional are registered as participants automatically.

## Required environment variables

```dotenv
LIVEKIT_URL=wss://livekit.example.com
LIVEKIT_API_KEY=replace-me
LIVEKIT_API_SECRET=replace-me
LIVEKIT_TOKEN_TTL_MINUTES=60
```

`LIVEKIT_API_SECRET` is read only by Spring Boot. Never expose it as a `VITE_*` variable. The React application requests a short-lived participant token from `POST /api/v1/video-sessions/{id}/token`.

## Workflow

1. A clinician creates a video session from an appointment or consultation.
2. The patient enters the waiting room.
3. The assigned professional starts the session and admits the patient.
4. Each participant requests their own signed token and joins the LiveKit room.
5. The clinician completes the session; join tokens can no longer be issued.

LiveKit participant identities and room names use opaque UUIDs, not names, email addresses, or other patient information.
