# Validation Services API

This document describes the new validation services API endpoints that provide step-by-step QR code validation.

## Overview

The validation services split the QR code validation workflow into individual steps, allowing clients to:

1. Check format recognition
2. Extract Key IDs (KID)
3. Determine environment (DEV/UAT/PROD)
4. Validate digital signatures
5. Extract QR content

## API Endpoints

All endpoints accept POST requests with JSON payloads.

### 1. Format Recognition

**Endpoint:** `POST /validation/format-recognition`

**Description:** Check if QR code is recognized by any supported format.

**Request:**
```json
{
  "uri": "HC1:6BF+70790T9WJWG..."
}
```

**Response:**
```json
{
  "recognized": true,
  "format": "HCERT",
  "description": "HCERT (EU DCC or WHO DDCC)"
}
```

### 2. Specific Format Recognition

**Endpoint:** `POST /validation/format-recognition/{format}`

**Description:** Check if QR code matches a specific format.

**Supported formats:** `hcert`, `shc`, `divoc_b64`, `divoc_pk`, `icao`

**Request:**
```json
{
  "uri": "HC1:6BF+70790T9WJWG..."
}
```

**Response:**
```json
{
  "recognized": true,
  "format": "HCERT",
  "description": "HCERT (EU DCC or WHO DDCC)"
}
```

### 3. KID Extraction

**Endpoint:** `POST /validation/kid-extraction`

**Description:** Extract Key ID (KID) from QR code if present.

**Request:**
```json
{
  "uri": "HC1:6BF+70790T9WJWG..."
}
```

**Response:**
```json
{
  "hasKid": true,
  "kid": "hcert_kid_present",
  "format": "HCERT"
}
```

### 4. KID Environment Check

**Endpoint:** `POST /validation/kid-environment`

**Description:** Check which environment (DEV/UAT/PROD) a KID belongs to.

**Request:**
```json
{
  "kid": "test_kid_123"
}
```

**Response:**
```json
{
  "environment": "PRODUCTION",
  "kid": "test_kid_123",
  "registryFound": true
}
```

### 5. Signature Validation

**Endpoint:** `POST /validation/signature-validation`

**Description:** Validate digital signature of QR code.

**Request:**
```json
{
  "uri": "HC1:6BF+70790T9WJWG..."
}
```

**Response:**
```json
{
  "signatureValid": true,
  "kid": "hcert_kid_present",
  "issuer": {
    "displayName": {"en": "Issuer Name"},
    "status": "CURRENT",
    "scope": "PRODUCTION"
  },
  "validationStatus": "VERIFIED"
}
```

### 6. Content Extraction

**Endpoint:** `POST /validation/content-extraction`

**Description:** Extract content from QR code.

**Request:**
```json
{
  "uri": "HC1:6BF+70790T9WJWG..."
}
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

## Error Responses

When validation fails, responses include error details:

```json
{
  "recognized": false,
  "format": null,
  "description": "Unrecognized QR format"
}
```

## Supported QR Formats

- **HCERT** (prefix: `HC1:`): EU DCC or WHO DDCC certificates
- **SHC** (prefix: `SHC:`): Smart Health Cards
- **DIVOC_B64** (prefix: `B64:`): DIVOC Base64 encoded
- **DIVOC_PK** (prefix: `PK`): DIVOC Plain Key format
- **ICAO** (contains: `ICAO`): ICAO travel documents

## Validation Status Values

The signature validation endpoint returns detailed status information:

- `VERIFIED`: Certificate is valid and trusted
- `INVALID_SIGNATURE`: Signature verification failed
- `KID_NOT_INCLUDED`: No Key ID found in certificate
- `ISSUER_NOT_TRUSTED`: Issuer not in trust registry
- `TERMINATED_KEYS`: Issuer keys have been terminated
- `EXPIRED_KEYS`: Issuer keys have expired
- `REVOKED_KEYS`: Issuer keys have been revoked
- `INVALID_ENCODING`: QR code encoding is invalid
- `INVALID_COMPRESSION`: Content decompression failed
- `INVALID_SIGNING_FORMAT`: Digital signature format is invalid

## Usage Examples

### Check if QR is valid EU DCC
```bash
curl -X POST http://localhost:8080/validation/format-recognition/hcert \
  -H "Content-Type: application/json" \
  -d '{"uri": "HC1:6BF+70790T9WJWG..."}'
```

### Extract content from any supported QR
```bash
curl -X POST http://localhost:8080/validation/content-extraction \
  -H "Content-Type: application/json" \
  -d '{"uri": "SHC:/56762909524320..."}'
```

### Validate signature
```bash
curl -X POST http://localhost:8080/validation/signature-validation \
  -H "Content-Type: application/json" \
  -d '{"uri": "B64:UEsDBBQACAAIAAAAAAAAAAAAAAAAAAAAAAAQAAAAY2VydGlmaWNhdGUuanNvb..."}'
```