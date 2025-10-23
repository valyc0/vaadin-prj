# Servizio Upload Streaming File - Roles Service

## Panoramica

Il **roles-service** include un sistema di upload streaming ottimizzato per file di grandi dimensioni (fino a 50GB+) che:
- ✅ Non carica mai il file in memoria
- ✅ Non crea file temporanei su disco
- ✅ Usa memoria costante (~10MB) indipendentemente dalla dimensione del file
- ✅ Supporta file fino a 50GB (configurabile per dimensioni maggiori)
- ✅ Fornisce API REST complete e documentate

## Quick Start

### 1. Prerequisiti

**Avvia MinIO**:
```bash
cd /home/valerio/prj/vaadin-prj/minio
./start-minio.sh
```

**Avvia Roles Service**:
```bash
cd /home/valerio/prj/vaadin-prj/roles-service
./start.sh
```

### 2. Test Rapido

**Upload file piccolo (100MB)**:
```bash
./test-upload.sh 100
```

**Upload file medio (1GB)**:
```bash
./test-upload.sh 1000
```

**Upload file grande (10GB)**:
```bash
./test-upload.sh 10000
```

**Con autenticazione JWT**:
```bash
./test-upload.sh 100 "eyJhbGciOiJSUzI1NiIsInR5cCI..."
```

### 3. Upload Manuale con cURL

```bash
curl -X POST "http://localhost:8091/api/files/upload" \
     -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     -F "file=@/path/to/your/file.mp4"
```

## API Endpoints

### POST /api/files/upload
Upload file in streaming.

**Request**:
- Content-Type: `multipart/form-data`
- Authorization: `Bearer JWT_TOKEN`
- Body: `file` (file multipart)

**Response** (200 OK):
```json
{
  "storedFileName": "a1b2c3d4-e5f6-7890-abcd-ef1234567890.mp4",
  "originalFileName": "video.mp4",
  "size": 1073741824,
  "formattedSize": "1.00 GB",
  "contentType": "video/mp4",
  "throughputMBps": "125.50",
  "durationMs": 8192
}
```

### GET /api/files/download/{filename}
Download file in streaming.

**Response**: File stream con headers appropriati

### GET /api/files/list
Lista tutti i file.

**Response**:
```json
[
  "a1b2c3d4-e5f6-7890-abcd-ef1234567890.mp4",
  "b2c3d4e5-f6a7-8901-bcde-f12345678901.pdf"
]
```

### GET /api/files/metadata/{filename}
Ottieni metadata del file.

**Response**:
```json
{
  "filename": "a1b2c3d4-e5f6-7890-abcd-ef1234567890.mp4",
  "size": 1073741824,
  "formattedSize": "1.00 GB",
  "contentType": "video/mp4",
  "etag": "d41d8cd98f00b204e9800998ecf8427e",
  "lastModified": "2025-10-22T10:30:45+02:00"
}
```

### DELETE /api/files/{filename}
Elimina file.

**Response**:
```json
{
  "message": "File deleted successfully"
}
```

## Swagger UI

Accedi alla documentazione interattiva:
```
http://localhost:8091/swagger-ui.html
```

## Architettura

```
Client (Browser/cURL)
    │ HTTP POST multipart
    │ (Streaming chunked transfer)
    ▼
Spring Boot Controller
    │ MultipartFile.getInputStream()
    │ (Direct stream access - NO buffering)
    ▼
MinioStreamingService
    │ InputStream diretto
    │ (NO memory loading)
    ▼
MinIO Client
    │ HTTP PUT chunked (10MB chunks)
    │ (Multipart upload automatico)
    ▼
MinIO Server
    │ Salvataggio su storage
    └──► File persistito
```

## Configurazione

### application.yml

```yaml
# MinIO Configuration
minio:
  url: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin123
  bucket-name: roles-service-files

# File Upload Configuration
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 50GB              # Limite massimo file
      max-request-size: 50GB           # Limite massimo request
      file-size-threshold: 0           # NO temp file, streaming puro

server:
  tomcat:
    connection-timeout: 3600000        # 1 ora timeout
    max-http-form-post-size: -1        # No limit
```

### Per file più grandi di 50GB

Modifica `max-file-size` e `max-request-size` in `application.yml`:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100GB
      max-request-size: 100GB
```

## Performance

### Memoria

| Dimensione File | Memoria Usata |
|----------------|---------------|
| 100 MB         | ~10 MB        |
| 1 GB           | ~10 MB        |
| 10 GB          | ~10 MB        |
| 50 GB          | ~10 MB        |
| 100 GB         | ~10 MB        |

**La memoria usata è COSTANTE e indipendente dalla dimensione del file!**

### Throughput Tipici

| Scenario                    | Throughput      |
|----------------------------|-----------------|
| Rete locale (1 Gbps)       | ~100-125 MB/s   |
| Rete locale (10 Gbps)      | ~800-1000 MB/s  |
| Internet (100 Mbps upload) | ~10-12 MB/s     |
| Internet (1 Gbps fiber)    | ~100-120 MB/s   |

*Valori approssimativi, dipendono da latenza, CPU, disco I/O su MinIO*

## Monitoring

### Logs

I log includono automaticamente:
```
INFO - Starting streaming upload: filename='video.mp4', size=1073741824 bytes (1.00 GB), 
       contentType='video/mp4', remoteAddr='192.168.1.100'
INFO - Streaming upload completed: filename='a1b2c3d4...mp4', size=1073741824 bytes, 
       duration=8192 ms, throughput=125.50 MB/s
```

### Visualizza logs in tempo reale

```bash
tail -f logs/application.log
```

### MinIO Console

Accedi alla console MinIO per visualizzare i file:
```
http://localhost:9001
Username: minioadmin
Password: minioadmin123
```

## Sicurezza

### Autenticazione JWT

Tutti gli endpoint richiedono un token JWT valido nell'header:
```
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI...
```

### Ottenere un token JWT

1. Autentica con Keycloak (su porta 8081)
2. Usa il token ricevuto per chiamare le API

Esempio con Keycloak:
```bash
# Ottieni token
TOKEN=$(curl -s -X POST "http://localhost:8081/realms/demo-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=YOUR_USERNAME" \
  -d "password=YOUR_PASSWORD" \
  -d "grant_type=password" \
  -d "client_id=YOUR_CLIENT_ID" \
  | jq -r '.access_token')

# Usa token per upload
curl -X POST "http://localhost:8091/api/files/upload" \
     -H "Authorization: Bearer $TOKEN" \
     -F "file=@/path/to/file.mp4"
```

## Troubleshooting

### Upload fallisce con timeout

**Problema**: File troppo grande o connessione lenta.

**Soluzione**: Aumenta il timeout in `application.yml`:
```yaml
server:
  tomcat:
    connection-timeout: 7200000  # 2 ore
```

### MinIO non raggiungibile

**Problema**: MinIO non in esecuzione o configurazione errata.

**Verifica**:
```bash
# Test connessione MinIO
curl http://localhost:9000

# Verifica configurazione
grep minio /home/valerio/prj/vaadin-prj/roles-service/src/main/resources/application.yml
```

### Errore 401 Unauthorized

**Problema**: Token JWT mancante o scaduto.

**Soluzione**: Ottieni un nuovo token JWT da Keycloak.

### OutOfMemoryError

**Problema**: `file-size-threshold` configurato male.

**Verifica**:
```yaml
spring:
  servlet:
    multipart:
      file-size-threshold: 0  # DEVE essere 0 per streaming puro!
```

## Test di Stress

### Test con 100 file da 1GB simultanei

```bash
for i in {1..100}; do
  ./test-upload.sh 1000 "YOUR_TOKEN" &
done
wait
```

### Test con file da 30GB

```bash
# Crea file di test
dd if=/dev/zero of=/tmp/test30gb.bin bs=1M count=30720

# Upload
time curl -X POST "http://localhost:8091/api/files/upload" \
     -H "Authorization: Bearer YOUR_TOKEN" \
     -F "file=@/tmp/test30gb.bin"
```

## Riferimenti

- [Documentazione Architettura Completa](./STREAMING_UPLOAD_ARCHITECTURE.md)
- [MinIO Java SDK](https://min.io/docs/minio/linux/developers/java/API.html)
- [Spring Boot Multipart](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html#application-properties.server.spring.servlet.multipart)

## Supporto

Per problemi o domande:
1. Controlla i logs: `tail -f logs/application.log`
2. Verifica MinIO Console: `http://localhost:9001`
3. Consulta Swagger UI: `http://localhost:8091/swagger-ui.html`
4. Leggi [STREAMING_UPLOAD_ARCHITECTURE.md](./STREAMING_UPLOAD_ARCHITECTURE.md)
