# Offline-First Synchronization Architecture & Implementation

## 1. Overview & Goal

Field Notes is an offline-first mobile system designed for field technicians operating in remote environments with intermittent or absent network connectivity. Technicians can create and edit customers, sites, and notes while offline, then synchronize with the backend once connectivity is restored.

The synchronization mechanism guarantees:
- **No data loss** or silent overwriting of concurrent changes.
- **Foreign-key integrity** preserving dependency order (customers → sites → field notes).
- **Client-generated UUIDs** as stable identifiers across offline and online states.
- **Idempotency** for safe retry of network requests after timeouts.
- **Strict tenant/user isolation** preventing any user from reading or modifying another user's data.

---

## 2. API Endpoint Specification

### `POST /api/fieldnotesatl/sync`

- **Authentication**: Required (`Authorization: Bearer <jwt_access_token>`).
- **Content-Type**: `application/json`

### Request Payload (`SyncRequest`)

```json
{
  "deviceId": "flutter-device-uuid-or-id",
  "lastSyncAt": "2026-10-12T18:00:00Z",
  "changes": {
    "customers": [
      {
        "id": "11111111-1111-1111-1111-111111111111",
        "name": "Acme Industrial Corp",
        "contactName": "Alice Johnson",
        "contactPhone": "+14045550199",
        "contactEmail": "alice@acme.com",
        "notes": "Primary industrial site client",
        "version": 0
      }
    ],
    "sites": [
      {
        "id": "22222222-2222-2222-2222-222222222222",
        "customerId": "11111111-1111-1111-1111-111111111111",
        "name": "Peachtree Warehouse #4",
        "addressLine1": "100 Peachtree St NW",
        "addressLine2": "Bay 3",
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
        "title": "HVAC Compressor Pressure Check",
        "description": "Inspected Unit 4; pressure nominal at 120 PSI.",
        "noteDate": "2026-10-12T18:15:00Z",
        "status": "COMPLETED",
        "latitude": 33.753746,
        "longitude": -84.386330,
        "photoReference": "photos/compressor_gauge.jpg",
        "version": 0
      }
    ],
    "deletedCustomerIds": [],
    "deletedSiteIds": [],
    "deletedFieldNoteIds": []
  }
}
```

### Response Payload (`SyncResponse`)

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

## 3. Core Synchronization Algorithm (Single Atomic Transaction)

Each call to `/api/fieldnotesatl/sync` executes within a **single atomic transaction** (`@Transactional` in `SyncService`). Both the pushed mutations and the delta pull query execute in the same transaction context, committing at the end of the method:

```
Client POST /sync
   │
   ├─► 1. Authenticate user & resolve previous cursor
   │      PREFERENCE: Server-stored DeviceSyncCursor is authoritative.
   │      A client-supplied lastSyncAt is ONLY honored if it is EARLIER than
   │      the stored cursor (enabling intentional re-pulls, never skipping data).
   │
   ├─► 2. PUSH PHASE: Process client batch in dependency order:
   │      a. Customers upserts (idempotent: matching content treated as already applied)
   │      b. Sites upserts (verify customer exists, owned by user, and not deleted)
   │      c. Field Notes upserts (verify site exists, owned by user, and not deleted)
   │      d. Soft-deletes with cascade:
   │         - Customer delete cascades soft-deletes to all child sites & notes
   │         - Site delete cascades soft-deletes to all child notes
   │
   ├─► 3. CAPTURE NEW CURSOR:
   │      Captured using the database clock (SELECT CURRENT_TIMESTAMP)
   │      BEFORE the pull queries run.
   │
   ├─► 4. PULL PHASE (with Overlap Window):
   │      Queries records updated > (previousCursor - 2 seconds).
   │      The 2-second overlap window eliminates missed updates from concurrent
   │      transaction commits or clock skew. The client upserts by UUID and version,
   │      so duplicates are harmless.
   │
   ├─► 5. Persist device cursor (device_sync_cursors table)
   │
   └─► 6. Return SyncResponse (newCursor, deltas, tombstones, conflicts)
```

---

## 4. Conflict Resolution & Idempotency Rules

1. **Idempotent Retry**:
   If a client retries an identical push (e.g. after a network timeout), the server compares the submitted content against current record state. If content matches, it is treated as already applied: no version increment occurs and `conflicts` is empty.
2. **New Record (Insert)**:
   If no record exists with the client-provided UUID, it is inserted with server version 0.
3. **Matching Versions (Update)**:
   If client version equals or exceeds server version, fields are updated and server version is incremented.
4. **Server Version Ahead (Conflict)**:
   If `server.version > client.version` and content differs, the change is NOT overwritten. A `SyncConflict` descriptor is returned and the authoritative server record is returned in the pull payload.
5. **Parent Soft-Deleted (Conflict)**:
   If a client attempts to insert or update a site whose customer is soft-deleted, or a field note whose site is soft-deleted, a conflict is returned.

---

## 5. Deletion Tombstones & Retention

- **Soft Delete Cascades**: Deleting a customer marks the customer, all its sites, and all its field notes as soft-deleted (`deleted_at = now()`, `version = version + 1`).
- **REST and Sync Parity**: Both `CustomerController`/`CustomerService` and `SyncService` execute the identical cascading soft-delete logic.
- **Tombstone Delivery**: During pull, any records where `deleted_at > (previousCursor - 2s)` have their IDs returned in `deletedCustomerIds`, `deletedSiteIds`, or `deletedFieldNoteIds`.
- **Tombstone Retention**: Tombstones are retained indefinitely in the current release to support long-disconnected field devices.
