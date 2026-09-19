# HEALTH'YS Web

## Start locally

```bash
cp .env.example .env
npm install
npm run dev
```

The Keycloak client `healthys-web` must allow
`http://localhost:5173/*` as a redirect URI and
`http://localhost:5173` as a web origin.

The authentication flow is Authorization Code with PKCE. No client secret is
stored in the browser. The protected `/me` page calls
`GET /api/v1/persons/me` with a refreshed bearer token.
