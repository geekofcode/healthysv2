# Healthys API conventions

## URLs and HTTP

All business endpoints start with `/api/v1`. Resource names are plural,
lowercase nouns, for example `/api/v1/patients`.

- `GET`: read resources and never mutate state.
- `POST`: create a resource, return `201 Created`, and provide `Location`.
- `PUT`: replace a complete resource.
- `PATCH`: apply a partial update.
- `DELETE`: return `204 No Content` when deletion succeeds.
- `200 OK`: successful read or update.
- `400 Bad Request`: malformed input or Jakarta Validation failure.
- `401 Unauthorized` / `403 Forbidden`: authentication or authorization.
- `404 Not Found`: unknown resource.
- `409 Conflict`: uniqueness, state, or persistence conflict.
- `422 Unprocessable Entity`: valid input rejected by a business rule.
- `500 Internal Server Error`: unexpected failure without internal details.

## DTOs

Controllers never expose JPA entities. DTOs are immutable Java records:

- input contracts end with `Request`;
- output contracts end with `Response`;
- creation and update contracts remain separate;
- Jakarta Validation annotations belong on request components;
- identifiers use `UUID`;
- timestamps use ISO-8601 `Instant`;
- MapStruct mappers use `CentralMapperConfig`.

Unmapped MapStruct target properties fail compilation. Null source properties are
ignored during updates.

## Pagination

Collection endpoints accept zero-based `page` and `size` parameters.
Defaults are `page=0` and `size=20`; the maximum size is 100. Responses use
`PageResponse<T>`:

```json
{
  "content": [],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true
  }
}
```

## Errors and correlation

Every response contains the `X-Correlation-ID` header. A valid caller-provided
identifier is preserved; otherwise the API generates a UUID. The same value is
available in logs through MDC and in error bodies.

```json
{
  "timestamp": "2026-09-18T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/patients",
  "correlationId": "6b7a4ce1-3a73-4e97-8ba0-c4e135bb5554",
  "violations": [
    {
      "field": "firstName",
      "message": "must not be blank"
    }
  ]
}
```

Internal exception details, SQL messages, credentials, and stack traces are
never returned to clients.

## Audit metadata

Persistent aggregate roots that have audit columns extend
`AuditableEntity`. Spring Data fills `createdAt`, `updatedAt`,
`createdBy`, and `updatedBy`; `version` provides optimistic locking. The
authenticated Keycloak subject is used as auditor when it is a UUID.

## OpenAPI

The specification is available at `/v3/api-docs` and Swagger UI at
`/swagger-ui.html`. Secured endpoints use the global `bearerAuth` JWT
scheme.
