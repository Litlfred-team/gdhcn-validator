# Validation Services

This document describes the new validation services that provide step-by-step QR code validation functionality for the GDHCN Validator web application.

## Overview

The validation services break down the QR code validation process into discrete steps, allowing applications to:

- **Unified QR code validation** - Complete validation with format detection, signature verification, and content extraction  
- **Extract Key IDs** - Find and extract cryptographic key identifiers
- **Check environments** - Determine if keys belong to DEV, UAT, or PROD environments  
- **Validate signatures** - Perform cryptographic signature verification
- **Extract content** - Decode and return QR code payload

This granular approach enables more flexible integration and better error handling compared to the all-or-nothing verification approach.

## Supported QR Code Formats

| Format | Prefix | Description |
|--------|--------|-------------|
| HCERT | `HC1:` | EU Digital COVID Certificate or WHO DDCC |
| SHC | `SHC:` | Smart Health Cards (US/International) |
| DIVOC B64 | `B64:` | DIVOC India Digital Health Certificate (Base64) |
| DIVOC PK | `PK` | DIVOC India Digital Health Certificate (Plain Key) |
| ICAO | `ICAO` | ICAO Travel Documents |

## API Endpoints

### Base URL
```
http://localhost:8080/validation/
```

### 1. QR Code Validation

**Endpoint:** `POST /validate-code`

Validates a QR code either from string content or uploaded image. Supports optional format filtering for HC1/HCERT format.

**For string content:**
```bash
curl -X POST http://localhost:8080/validation/validate-code \
  -H "Content-Type: application/json" \
  -d '{"content": "HC1:6BF+70790T9WJWG..."}'
```

**For image upload:**
```bash
curl -X POST http://localhost:8080/validation/validate-code \
  -F "file=@qr_code_image.png"
```

**With format filtering:**
```bash
curl -X POST "http://localhost:8080/validation/validate-code?format=HC1" \
  -H "Content-Type: application/json" \
  -d '{"content": "HC1:6BF+70790T9WJWG..."}'
```

**Response (Valid Certificate - HTTP 200):**
```json
{
  "valid": true,
  "format": "HC1",
  "issuer": "WHO/Country Name",
  "details": {
    "status": "VERIFIED",
    "kid": "key_id",
    "environment": "PRODUCTION",
    "signatureValid": true,
    "content": "decoded_content"
  }
}
```

**Response (Invalid Certificate - HTTP 422):**
```json
{
  "valid": false,
  "format": "HC1",
  "issuer": null,
  "details": {
    "status": "INVALID_SIGNATURE",
    "kid": "key_id",
    "environment": null,
    "signatureValid": false,
    "content": "decoded_content"
  }
}
```

**HTTP Status Codes:**
- **200**: Valid certificate with full validation details
- **400**: Invalid input format or unsupported format parameter
- **404**: QR code format not recognized or QR code not found in image
- **422**: Invalid certificate content (bad signature, untrusted issuer, expired keys, etc.)

### 2. KID Extraction

**Endpoint:** `POST /kid-extraction`

Extract Key ID (KID) from QR code if present.

```bash
curl -X POST http://localhost:8080/validation/kid-extraction \
  -H "Content-Type: application/json" \
  -d '{"uri": "HC1:6BF+70790T9WJWG..."}'
```

**Response:**
```json
{
  "hasKid": true,
  "kid": "extracted_from_hcert",
  "format": "HCERT"
}
```

### 3. KID Environment Check

**Endpoint:** `POST /kid-environment`

Check which environment (DEV/UAT/PROD) a KID belongs to.

```bash
curl -X POST http://localhost:8080/validation/kid-environment \
  -H "Content-Type: application/json" \
  -d '{"kid": "test_kid_123"}'
```

**Response:**
```json
{
  "environment": "PRODUCTION",
  "kid": "test_kid_123",
  "registryFound": true
}
```

### 4. Signature Validation

**Endpoint:** `POST /signature-validation`

Validate digital signature of QR code.

```bash
curl -X POST http://localhost:8080/validation/signature-validation \
  -H "Content-Type: application/json" \
  -d '{"uri": "HC1:6BF+70790T9WJWG..."}'
```

**Response:**
```json
{
  "signatureValid": true,
  "kid": "extracted_from_hcert",
  "issuer": {
    "displayName": {"en": "Issuer Name"},
    "status": "CURRENT",
    "scope": "PRODUCTION"
  },
  "validationStatus": "VERIFIED"
}
```

### 5. Content Extraction

**Endpoint:** `POST /content-extraction`

Extract content from QR code.

```bash
curl -X POST http://localhost:8080/validation/content-extraction \
  -H "Content-Type: application/json" \
  -d '{"uri": "HC1:6BF+70790T9WJWG..."}'
```

**Response:**
```json
{
  "contentExtracted": true,
  "content": "{\"1\": {\"v\": [...]}}", 
  "format": "HCERT",
  "error": null
}
```

## Validation Status Values

The signature validation endpoint returns detailed status information:

| Status | Description |
|--------|-------------|
| `VERIFIED` | Certificate is valid and trusted |
| `INVALID_SIGNATURE` | Signature verification failed |
| `KID_NOT_INCLUDED` | No Key ID found in certificate |
| `ISSUER_NOT_TRUSTED` | Issuer not in trust registry |
| `TERMINATED_KEYS` | Issuer keys have been terminated |
| `EXPIRED_KEYS` | Issuer keys have expired |
| `REVOKED_KEYS` | Issuer keys have been revoked |
| `INVALID_ENCODING` | QR code encoding is invalid |
| `INVALID_COMPRESSION` | Content decompression failed |
| `INVALID_SIGNING_FORMAT` | Digital signature format is invalid |

## Environment Scopes

| Scope | Description |
|-------|-------------|
| `PRODUCTION` | Production/live environment |
| `ACCEPTANCE_TEST` | UAT/Acceptance testing environment |
| `DEV_STAGING` | Development/staging environment |

## Example Workflow

Here's an example of how to use the validation services in sequence:

### 1. Validate QR Code
```bash
# Validate QR code (all-in-one validation with format detection)
curl -X POST http://localhost:8080/validation/validate-code \
  -H "Content-Type: application/json" \
  -d '{"content": "HC1:6BF+70790T9WJWG..."}'
```

### 2. Validate with Format Filter
```bash
# Validate QR code with specific format requirement
curl -X POST "http://localhost:8080/validation/validate-code?format=HC1" \
  -H "Content-Type: application/json" \
  -d '{"content": "HC1:6BF+70790T9WJWG..."}'
```

### 3. Extract KID
```bash
# Check if the QR contains a Key ID
curl -X POST http://localhost:8080/validation/kid-extraction \
  -H "Content-Type: application/json" \
  -d '{"uri": "HC1:6BF+70790T9WJWG..."}'
```

### 4. Check Environment
```bash
# Determine which environment the KID belongs to
curl -X POST http://localhost:8080/validation/kid-environment \
  -H "Content-Type: application/json" \
  -d '{"kid": "extracted_kid_value"}'
```

### 5. Validate Signature
```bash
# Perform cryptographic validation
curl -X POST http://localhost:8080/validation/signature-validation \
  -H "Content-Type: application/json" \
  -d '{"uri": "HC1:6BF+70790T9WJWG..."}'
```

### 6. Extract Content
```bash
# Finally, extract the decoded content
curl -X POST http://localhost:8080/validation/content-extraction \
  -H "Content-Type: application/json" \
  -d '{"uri": "HC1:6BF+70790T9WJWG..."}'
```

## Error Handling

All endpoints return structured error responses when validation fails:

```json
{
  "recognized": false,
  "format": null,
  "description": "Unrecognized QR format"
}
```

For content extraction errors:
```json
{
  "contentExtracted": false,
  "content": null,
  "format": "HCERT",
  "error": "Failed to extract content: INVALID_ENCODING"
}
```

## Integration Notes

- All endpoints require `Content-Type: application/json` headers
- QR content should be passed as-is (no URL encoding needed for the URI field)
- Response times vary based on validation complexity (format recognition is fastest, signature validation is slowest)
- The services use the same trust registry as the main validation endpoint
- KID extraction may require partial QR processing, so it's more expensive than format recognition

## Testing

A comprehensive test script is provided at `test_validation_endpoints.sh` that demonstrates all endpoints with sample QR codes.

Run tests:
```bash
./test_validation_endpoints.sh
```

The script requires `jq` for JSON formatting and a running instance of the web application.