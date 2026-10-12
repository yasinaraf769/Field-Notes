# Field Notes Backend & Database Integration Guide for Flutter

> **Target Audience**: Flutter Mobile Application Developer  
> **Backend Stack**: Spring Boot 3.5, Java 21, PostgreSQL (Neon Serverless DB), Docker on Render  
> **Last Updated**: October 2026  
> **API Version**: `v1` (Base Path: `/api/fieldnotesatl`)

---

## 1. Quick Reference & Connection Details

### Environment URLs

| Environment | Base URL | Swagger / OpenAPI Docs |
|---|---|---|
| **Production (Live)** | `https://field-notes-63xj.onrender.com/api/fieldnotesatl` | [Swagger UI](https://field-notes-63xj.onrender.com/swagger-ui.html) · [OpenAPI JSON](https://field-notes-63xj.onrender.com/v3/api-docs) |
| **Local (Android Emulator)** | `http://10.0.2.2:8080/api/fieldnotesatl` | `http://10.0.2.2:8080/swagger-ui.html` |
| **Local (iOS Simulator / Desktop)** | `http://localhost:8080/api/fieldnotesatl` | `http://localhost:8080/swagger-ui.html` |
| **Health Check** | `https://field-notes-63xj.onrender.com/actuator/health` | Returns `{"status":"UP"}` |

### Common Headers
```http
Content-Type: application/json
Accept: application/json
Authorization: Bearer <ACCESS_TOKEN>   # (Required for all endpoints except /auth/* and /actuator/*)
```

---

## 2. Database Model & Entity Schema

The database runs on **Neon PostgreSQL** with UUIDs as primary keys. All entities follow **multi-tenancy scoped to the authenticated user** and include optimistic concurrency locking (`version`) and soft-delete support (`deleted_at`).

```
┌────────────────┐       1:N       ┌────────────────┐       1:N       ┌────────────────┐
│   customers    │ ─────────────── │     sites      │ ─────────────── │  field_notes   │
└────────────────┘                 └────────────────┘                 └────────────────┘
```

### 2.1 Customer (`customers`)
Represents an organization or client entity.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | `UUID` (string) | No | Primary key. In offline mode, Flutter generates this with `uuid.v4()`. |
| `name` | `String (max 255)` | No | Customer business or individual name. |
| `contact_name` | `String (max 255)` | Yes | Primary contact person. |
| `contact_phone` | `String (max 50)` | Yes | Phone number. |
| `contact_email` | `String (max 255)` | Yes | Valid email format. |
| `notes` | `String (TEXT)` | Yes | General notes. |
| `version` | `int64 / long` | No | Version counter (starts at 0, incremented on updates). |
| `created_at` | `ISO-8601 UTC` | No | Timestamp of creation. |
| `updated_at` | `ISO-8601 UTC` | No | Timestamp of last modification (used for sync sorting). |
| `deleted_at` | `ISO-8601 UTC` | Yes | Non-null if soft-deleted. |

### 2.2 Site (`sites`)
Represents a physical location associated with a customer.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | `UUID` (string) | No | Primary key (client-generated or server-generated). |
| `customer_id` | `UUID` (string) | No | Foreign key referencing `customers.id`. |
| `name` | `String (max 255)` | No | Name or code of the job site / facility. |
| `address_line_1` | `String (max 255)` | No | Street address. |
| `address_line_2` | `String (max 255)` | Yes | Suite / Unit / Building. |
| `city` | `String (max 120)` | Yes | City. |
| `state_province` | `String (max 120)` | Yes | State or Province code/name. |
| `postal_code` | `String (max 30)` | Yes | ZIP or Postal code. |
| `country` | `String (max 120)` | Yes | Country name or ISO code. |
| `latitude` | `double / decimal(9,6)` | Yes | GPS latitude coordinate. |
| `longitude` | `double / decimal(9,6)` | Yes | GPS longitude coordinate. |
| `version` | `int64 / long` | No | Concurrency version. |
| `created_at` | `ISO-8601 UTC` | No | Creation timestamp. |
| `updated_at` | `ISO-8601 UTC` | No | Modification timestamp. |
| `deleted_at` | `ISO-8601 UTC` | Yes | Non-null if soft-deleted. |

### 2.3 Field Note (`field_notes`)
Work log, inspection entry, or note taken on a site.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | `UUID` (string) | No | Primary key. |
| `site_id` | `UUID` (string) | No | Foreign key referencing `sites.id`. |
| `title` | `String (max 255)` | No | Short title of the note. |
| `description` | `String (TEXT)` | No | Full content / observations. |
| `note_date` | `ISO-8601 UTC` | Yes | Timestamp the observation took place. |
| `status` | `String (Enum)` | No | Values: `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`. |
| `latitude` | `double / decimal(9,6)` | Yes | Geo-tagging latitude. |
| `longitude` | `double / decimal(9,6)` | Yes | Geo-tagging longitude. |
| `photo_reference` | `String (max 500)` | Yes | S3 key / URI (e.g. `photos/uuid.jpg`). |
| `version` | `int64 / long` | No | Concurrency version. |
| `created_at` | `ISO-8601 UTC` | No | Creation timestamp. |
| `updated_at` | `ISO-8601 UTC` | No | Modification timestamp. |
| `deleted_at` | `ISO-8601 UTC` | Yes | Non-null if soft-deleted. |

---

## 3. Authentication & Session Management

All endpoints under `/api/fieldnotesatl/*` (except `/auth/*`) require a JWT Bearer token in the `Authorization` header.

### 3.1 Register User
- **Method**: `POST`
- **Path**: `/auth/register`
- **Request Body**:
  ```json
  {
    "email": "technician@fieldnotes.com",
    "password": "Password123!",
    "firstName": "Alex",
    "lastName": "Smith",
    "phone": "+1-555-0199"
  }
  ```
- **Response** (`201 Created`):
  ```json
  {
    "user": {
      "id": "c1f7a0b3-96e2-4521-8f4b-74b88e0ec3d2",
      "email": "technician@fieldnotes.com",
      "firstName": "Alex",
      "lastName": "Smith",
      "phone": "+1-555-0199"
    },
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "7c980209-77f6-49f3-855c-cfb3e15779bb",
    "tokenType": "Bearer"
  }
  ```

### 3.2 Login
- **Method**: `POST`
- **Path**: `/auth/login`
- **Request Body**:
  ```json
  {
    "email": "technician@fieldnotes.com",
    "password": "Password123!"
  }
  ```
- **Response** (`200 OK`): Same payload as Register (returns `accessToken`, `refreshToken`, and user summary).

### 3.3 Refresh Token
When the `accessToken` expires (default: 1 hour), the Flutter client sends the `refreshToken` to get a fresh `accessToken`.
- **Method**: `POST`
- **Path**: `/auth/refresh`
- **Request Body**:
  ```json
  {
    "refreshToken": "7c980209-77f6-49f3-855c-cfb3e15779bb"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "user": { ... },
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.new...",
    "refreshToken": "new-refresh-token-or-same",
    "tokenType": "Bearer"
  }
  ```

---

## 4. REST Endpoints Reference

### 4.1 Customers (`/customers`)

| Endpoint | Method | Description |
|---|---|---|
| `/customers` | `GET` | List all active customers for the authenticated user. |
| `/customers` | `POST` | Create a new customer. |
| `/customers/{id}` | `GET` | Get a customer by ID. |
| `/customers/{id}` | `PUT` | Update a customer (requires updated fields). |
| `/customers/{id}` | `DELETE` | Soft-delete a customer (sets `deleted_at`). |

#### Create Customer Example:
- **`POST /customers`**
- **Request Body**:
  ```json
  {
    "id": "e4a77e5d-4f11-47d3-9bc2-3860bb7da821",  // Optional; if omitted, server generates
    "name": "Acme Contracting",
    "contactName": "Robert Vance",
    "contactPhone": "555-0144",
    "contactEmail": "bob@acme.com",
    "notes": "Handles northeast region"
  }
  ```

---

### 4.2 Sites (`/sites`)

| Endpoint | Method | Description |
|---|---|---|
| `/sites` | `GET` | List all active sites. Can filter with `?customerId=<UUID>`. |
| `/sites` | `POST` | Create a new site under an existing customer. |
| `/sites/{id}` | `GET` | Get site details by ID. |
| `/sites/{id}` | `PUT` | Update site details. |
| `/sites/{id}` | `DELETE` | Soft-delete site. |

#### Create Site Example:
- **`POST /sites`**
- **Request Body**:
  ```json
  {
    "id": "b34e5671-88fc-4318-971c-4b552fa8c130",
    "customerId": "e4a77e5d-4f11-47d3-9bc2-3860bb7da821",
    "name": "Substation Alpha 4",
    "addressLine1": "100 Industrial Parkway",
    "addressLine2": "Bay 3",
    "city": "Atlanta",
    "stateProvince": "GA",
    "postalCode": "30301",
    "country": "USA",
    "latitude": 33.7490,
    "longitude": -84.3880
  }
  ```

---

### 4.3 Field Notes (`/field-notes`)

| Endpoint | Method | Description |
|---|---|---|
| `/field-notes` | `GET` | List field notes. Can filter with `?siteId=<UUID>`. |
| `/field-notes` | `POST` | Create a new field note. |
| `/field-notes/{id}` | `GET` | Get field note by ID. |
| `/field-notes/{id}` | `PUT` | Update title, description, status, photoReference. |
| `/field-notes/{id}` | `DELETE` | Soft-delete field note. |

#### Create Field Note Example:
- **`POST /field-notes`**
- **Request Body**:
  ```json
  {
    "id": "993f4e12-4011-4fa2-bc44-5cb8d234ea72",
    "siteId": "b34e5671-88fc-4318-971c-4b552fa8c130",
    "title": "Breaker Panel Thermal Inspection",
    "description": "Infrared scan shows 12°C elevation on main feed lug A.",
    "noteDate": "2026-10-13T08:30:00Z",
    "status": "SUBMITTED",
    "latitude": 33.7492,
    "longitude": -84.3882,
    "photoReference": "photos/site1-breaker.jpg"
  }
  ```

---

## 5. Offline-First Sync Protocol (`POST /sync`)

The core feature of Field Notes is offline capability. Technicians work in basements or remote locations without internet, save records locally, and synchronize when connectivity is restored.

### 5.1 Protocol Overview

```
Flutter Client                                              Backend API
      │                                                          │
      │─── 1. POST /sync ───────────────────────────────────────>│
      │    - deviceId: stable UUID/device string                 │
      │    - lastSyncAt: previous cursor (null on 1st run)       │
      │    - changes: dirty local creates/updates & deletes      │
      │                                                          │
      │                                2. Server processes push: │
      │                                   - Upserts entities     │
      │                                   - Checks concurrency   │
      │                                   - Applies soft deletes │
      │                                                          │
      │                                3. Server pulls delta:    │
      │                                   - Captures new cursor  │
      │                                   - Fetches changes > cursor
      │                                                          │
      │<── 4. Response (200 OK) ─────────────────────────────────│
      │    - newCursor: ISO-8601 server timestamp                │
      │    - customers, sites, fieldNotes: updated/new records   │
      │    - deletedCustomerIds, deletedSiteIds, etc.            │
      │    - conflicts: any version discrepancies                │
      │                                                          │
      │─── 5. Update local DB & save newCursor ──────────────────│
```

### 5.2 Sync Request Payload

```json
{
  "deviceId": "flutter-device-uuid-12345",
  "lastSyncAt": "2026-10-12T12:00:00Z",
  "changes": {
    "customers": [
      {
        "id": "e4a77e5d-4f11-47d3-9bc2-3860bb7da821",
        "name": "Acme Contracting",
        "contactName": "Robert Vance",
        "contactPhone": "555-0144",
        "contactEmail": "bob@acme.com",
        "notes": "Added while offline",
        "version": 0
      }
    ],
    "sites": [],
    "fieldNotes": [
      {
        "id": "993f4e12-4011-4fa2-bc44-5cb8d234ea72",
        "siteId": "b34e5671-88fc-4318-971c-4b552fa8c130",
        "title": "Offline Inspection",
        "description": "Checked transformer bushings.",
        "noteDate": "2026-10-13T01:15:00Z",
        "status": "DRAFT",
        "latitude": 33.7490,
        "longitude": -84.3880,
        "photoReference": null,
        "version": 0
      }
    ],
    "deletedCustomerIds": [],
    "deletedSiteIds": [],
    "deletedFieldNoteIds": ["7a112233-4455-6677-8899-aabbccddeeff"]
  }
}
```

> **Client Rules for Sync**:
> 1. Always generate stable UUIDs (`uuid.v4()`) on the device when creating items offline.
> 2. On the **very first sync**, pass `"lastSyncAt": null`. The server will return the entire database state for that user.
> 3. Store `newCursor` in secure local storage (e.g. SQLite / Hive / SharedPreferences) after every successful sync.
> 4. If `deletedCustomerIds`, `deletedSiteIds`, or `deletedFieldNoteIds` are present in the response, delete those records from the local SQLite/Hive database.

---

## 6. Standard Error Response Format

If an error occurs, the backend returns a uniform RFC-7807 compatible error payload:

```json
{
  "timestamp": "2026-10-13T02:47:12.500Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for object='createCustomerRequest'. Field error in 'name': Customer name is required",
  "path": "/api/fieldnotesatl/customers",
  "details": [
    "Customer name is required"
  ]
}
```

### Common HTTP Status Codes

| Code | Meaning | Handling in Flutter |
|---|---|---|
| `200 OK` | Request succeeded | Process body. |
| `201 Created` | Entity created successfully | Store in local database / state. |
| `400 Bad Request` | Validation failure or malformed JSON | Show validation message to user. |
| `401 Unauthorized` | Missing / invalid / expired JWT | Trigger token refresh; if refresh fails, logout to LoginScreen. |
| `403 Forbidden` | Access denied to resource | Show alert message. |
| `404 Not Found` | Entity not found | Handle missing record. |
| `409 Conflict` | Concurrency version mismatch | Prompt user or perform merge. |

---

## 7. Flutter Implementation Guide & Dart Code Snippets

### 7.1 Recommended Flutter Packages (`pubspec.yaml`)

```yaml
dependencies:
  flutter:
    sdk: flutter
  dio: ^5.7.0                     # HTTP client with interceptors
  flutter_secure_storage: ^9.2.2  # Store JWT & Refresh tokens securely
  uuid: ^4.5.1                    # Client-side offline UUID generation
  shared_preferences: ^2.3.2      # Store sync cursor and app state
  drift: ^2.20.0                  # (Recommended) Type-safe offline SQLite ORM
  # or sqflite: ^2.3.3
```

---

### 7.2 Dio API Client with Auto-Refresh Interceptor

Create a singleton `ApiClient` that injects the `Bearer` token and automatically attempts token refresh on `401 Unauthorized`:

```dart
// lib/core/network/api_client.dart
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class ApiClient {
  static const String baseUrl = 'https://field-notes-63xj.onrender.com/api/fieldnotesatl';
  
  final Dio dio = Dio(BaseOptions(
    baseUrl: baseUrl,
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 15),
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
  ));

  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  ApiClient() {
    dio.interceptors.add(
      QueuedInterceptorsWrapper(
        onRequest: (options, handler) async {
          final accessToken = await _storage.read(key: 'access_token');
          if (accessToken != null && !options.path.contains('/auth/')) {
            options.headers['Authorization'] = 'Bearer $accessToken';
          }
          return handler.next(options);
        },
        onError: (DioException error, handler) async {
          // If token expired, refresh automatically
          if (error.response?.statusCode == 401 && !error.requestOptions.path.contains('/auth/')) {
            final refreshed = await _refreshToken();
            if (refreshed) {
              final newAccessToken = await _storage.read(key: 'access_token');
              error.requestOptions.headers['Authorization'] = 'Bearer $newAccessToken';
              // Retry the original request
              final response = await dio.fetch(error.requestOptions);
              return handler.resolve(response);
            }
          }
          return handler.next(error);
        },
      ),
    );
  }

  Future<bool> _refreshToken() async {
    try {
      final refreshToken = await _storage.read(key: 'refresh_token');
      if (refreshToken == null) return false;

      final response = await Dio().post(
        '$baseUrl/auth/refresh',
        data: {'refreshToken': refreshToken},
      );

      if (response.statusCode == 200) {
        final newAccessToken = response.data['accessToken'];
        final newRefreshToken = response.data['refreshToken'] ?? refreshToken;
        await _storage.write(key: 'access_token', value: newAccessToken);
        await _storage.write(key: 'refresh_token', value: newRefreshToken);
        return true;
      }
    } catch (_) {
      // Refresh failed: user must re-login
      await _storage.deleteAll();
    }
    return false;
  }
}
```

---

### 7.3 Offline Sync Service Skeleton

```dart
// lib/features/sync/sync_service.dart
import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';
import '../../core/network/api_client.dart';

class SyncService {
  final ApiClient _apiClient;
  final Uuid _uuid = const Uuid();

  SyncService(this._apiClient);

  Future<void> performSync({
    List<Map<String, dynamic>> dirtyCustomers = const [],
    List<Map<String, dynamic>> dirtySites = const [],
    List<Map<String, dynamic>> dirtyFieldNotes = const [],
    List<String> deletedCustomerIds = const [],
    List<String> deletedSiteIds = const [],
    List<String> deletedFieldNoteIds = const [],
  }) async {
    final prefs = await SharedPreferences.getInstance();
    final deviceId = prefs.getString('device_id') ?? _getOrCreateDeviceId(prefs);
    final lastSyncAt = prefs.getString('last_sync_cursor'); // null on first sync

    final payload = {
      'deviceId': deviceId,
      'lastSyncAt': lastSyncAt,
      'changes': {
        'customers': dirtyCustomers,
        'sites': dirtySites,
        'fieldNotes': dirtyFieldNotes,
        'deletedCustomerIds': deletedCustomerIds,
        'deletedSiteIds': deletedSiteIds,
        'deletedFieldNoteIds': deletedFieldNoteIds,
      },
    };

    final response = await _apiClient.dio.post('/sync', data: payload);

    if (response.statusCode == 200) {
      final data = response.data;
      
      // 1. Save new cursor
      final newCursor = data['newCursor'];
      if (newCursor != null) {
        await prefs.setString('last_sync_cursor', newCursor);
      }

      // 2. Upsert pulled items into local database
      final incomingCustomers = data['customers'] as List? ?? [];
      final incomingSites = data['sites'] as List? ?? [];
      final incomingFieldNotes = data['fieldNotes'] as List? ?? [];
      // (Execute batch local DB upserts here)

      // 3. Purge soft-deleted records from local database
      final remoteDeletedNotes = data['deletedFieldNoteIds'] as List? ?? [];
      // (Execute local DB deletes where id in remoteDeletedNotes)
    }
  }

  String _getOrCreateDeviceId(SharedPreferences prefs) {
    var id = prefs.getString('device_id');
    if (id == null) {
      id = _uuid.v4();
      prefs.setString('device_id', id);
    }
    return id;
  }
}
```

---

## 8. Summary Checklist for the Flutter Developer

- [ ] Add `dio`, `flutter_secure_storage`, and `uuid` to `pubspec.yaml`.
- [ ] Configure `baseUrl` as `https://field-notes-63xj.onrender.com/api/fieldnotesatl`.
- [ ] Implement user registration & login screens storing `accessToken` and `refreshToken` in `FlutterSecureStorage`.
- [ ] Ensure local SQLite/Drift tables use `TEXT` primary keys storing client-generated `uuid.v4()` strings.
- [ ] Use `POST /sync` as the primary background and pull-to-refresh synchronization mechanism.
- [ ] Verify endpoints with Swagger UI at `https://field-notes-63xj.onrender.com/swagger-ui.html`.
