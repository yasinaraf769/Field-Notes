# REST API Specification

**Base Path**: `/api/fieldnotesatl`

All endpoints are rooted under the custom base path `/api/fieldnotesatl`.

---

## Authentication Endpoints

### POST `/api/fieldnotesatl/auth/register`

- **Authentication**: None
- **Request Body**:
  ```json
  {
    "email": "user@example.com",
    "password": "StrongPass123!",
    "firstName": "Jane",
    "lastName": "Doe",
    "phone": "+14045550100"
  }
  ```
- **Response 201**:
  ```json
  {
    "user": {
      "id": "uuid",
      "email": "user@example.com",
      "firstName": "Jane",
      "lastName": "Doe",
      "phone": "+14045550100"
    },
    "accessToken": "jwt",
    "refreshToken": "refresh-token"
  }
  ```
- **Validation**: Email format, password strength (>=8 chars, upper, lower, digit, special), name length limits.

### POST `/api/fieldnotesatl/auth/login`

- **Authentication**: None
- **Request Body**:
  ```json
  {
    "email": "user@example.com",
    "password": "StrongPass123!"
  }
  ```
- **Response 200**:
  ```json
  {
    "accessToken": "jwt",
    "refreshToken": "refresh-token",
    "tokenType": "Bearer"
  }
  ```

### POST `/api/fieldnotesatl/auth/refresh`

- **Authentication**: None
- **Request Body**:
  ```json
  {
    "refreshToken": "refresh-token"
  }
  ```
- **Response 200**: New access and refresh token pair.

### POST `/api/fieldnotesatl/auth/logout`

- **Authentication**: Valid access token
- **Response 200**: Success message, refresh tokens revoked.

---

## Customer Endpoints

### GET `/api/fieldnotesatl/customers`
- **Authentication**: Required
- **Query Params**: `page`, `size`, `sort`, `search`
- **Response 200**: Paginated list of customers for the authenticated user.

### GET `/api/fieldnotesatl/customers/{id}`
- **Authentication**: Required (strict tenant ownership check)
- **Response 200**: Customer details.

### POST `/api/fieldnotesatl/customers`
- **Authentication**: Required
- **Request Body**: Customer creation payload.
- **Response 201**: Created customer with server-assigned UUID.

### PUT `/api/fieldnotesatl/customers/{id}`
- **Authentication**: Required
- **Response 200**: Updated customer.

### PATCH `/api/fieldnotesatl/customers/{id}`
- **Authentication**: Required
- **Response 200**: Partially updated customer.

### DELETE `/api/fieldnotesatl/customers/{id}`
- **Authentication**: Required
- **Response 204**: Soft-deletes customer record.

---

## Site Endpoints

### GET `/api/fieldnotesatl/sites`
- **Authentication**: Required
- **Query Params**: `customerId`, `search`, `page`, `size`
- **Response 200**: Paginated list of sites.

### GET `/api/fieldnotesatl/sites/{id}`
- **Authentication**: Required
- **Response 200**: Site details.

### POST `/api/fieldnotesatl/sites`
- **Authentication**: Required
- **Request Body**: `customerId`, `name`, `addressLine1`, `addressLine2`, `city`, `stateProvince`, `postalCode`, `country`, `latitude`, `longitude`.
- **Validation**: Customer must exist and belong to authenticated user.
- **Response 201**: Created site.

### PUT `/api/fieldnotesatl/sites/{id}`
- **Authentication**: Required
- **Response 200**: Updated site.

### DELETE `/api/fieldnotesatl/sites/{id}`
- **Authentication**: Required
- **Response 204**: Soft-deletes site record.

---

## Field Note Endpoints

### GET `/api/fieldnotesatl/field-notes`
- **Authentication**: Required
- **Query Params**: `siteId`, `customerId`, `status`, `search`, `page`, `size`
- **Response 200**: Paginated list of field notes with joined customer and site info.

### GET `/api/fieldnotesatl/field-notes/{id}`
- **Authentication**: Required
- **Response 200**: Field note details.

### POST `/api/fieldnotesatl/field-notes`
- **Authentication**: Required
- **Request Body**: `siteId`, `title`, `description`, `noteDate`, `status`, `latitude`, `longitude`, `photoReference`.
- **Validation**: Site must exist and belong to a customer owned by authenticated user.
- **Response 201**: Created field note.

### PUT `/api/fieldnotesatl/field-notes/{id}`
- **Authentication**: Required
- **Response 200**: Updated field note.

### DELETE `/api/fieldnotesatl/field-notes/{id}`
- **Authentication**: Required
- **Response 204**: Soft-deletes field note record.

---

## Offline Synchronization Endpoint

### POST `/api/fieldnotesatl/sync`

- **Authentication**: Required (`Authorization: Bearer <jwt_access_token>`)
- **Summary**: Pushes local offline batch mutations (customers, sites, field notes, soft-deletes) and pulls all server changes since the client's last watermark cursor.
- **Request Body**:
  ```json
  {
    "deviceId": "device-uuid-1234",
    "lastSyncAt": "2026-10-12T18:00:00Z",
    "changes": {
      "customers": [
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Acme Corp",
          "contactName": "Alice",
          "contactPhone": "+14045550100",
          "contactEmail": "alice@acme.com",
          "notes": "VIP Client",
          "version": 0
        }
      ],
      "sites": [
        {
          "id": "22222222-2222-2222-2222-222222222222",
          "customerId": "11111111-1111-1111-1111-111111111111",
          "name": "Headquarters",
          "addressLine1": "100 Peachtree St",
          "city": "Atlanta",
          "stateProvince": "GA",
          "postalCode": "30303",
          "country": "USA",
          "latitude": 33.753746,
          "longitude": -84.386330,
          "version": 0
        }
      ],
      "fieldNotes": [
        {
          "id": "33333333-3333-3333-3333-333333333333",
          "siteId": "22222222-2222-2222-2222-222222222222",
          "title": "HVAC Check",
          "description": "Inspected compressor",
          "status": "COMPLETED",
          "latitude": 33.753746,
          "longitude": -84.386330,
          "version": 0
        }
      ],
      "deletedCustomerIds": [],
      "deletedSiteIds": [],
      "deletedFieldNoteIds": []
    }
  }
  ```
- **Response 200**:
  ```json
  {
    "newCursor": "2026-10-12T18:30:00Z",
    "customers": [...],
    "sites": [...],
    "fieldNotes": [...],
    "deletedCustomerIds": [],
    "deletedSiteIds": [],
    "deletedFieldNoteIds": [],
    "conflicts": []
  }
  ```

---

## Error Format

Consistent structure across all endpoints:

```json
{
  "timestamp": "2026-10-12T18:45:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/fieldnotesatl/customers",
  "details": [
    {
      "field": "name",
      "message": "Customer name must not be blank"
    }
  ]
}
```

## Status Codes

- **200 OK**: Successful retrieval, update, or sync batch.
- **201 Created**: Resource created successfully.
- **204 No Content**: Deletion success.
- **400 Bad Request**: Validation failure or malformed payload.
- **401 Unauthorized**: Missing or expired JWT access token.
- **403 Forbidden**: Cross-user ownership violation or unauthorized action.
- **404 Not Found**: Resource does not exist.
- **409 Conflict**: Optimistic lock conflict or duplicate unique key.
- **500 Internal Server Error**: Unexpected unhandled failure.
