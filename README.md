# Vaadin Microservice Project - Modular Architecture

Progetto riorganizzato con architettura modulare Maven multi-modulo.

## Struttura del Progetto

```
vaadin-prj/
├── pom.xml                    # Parent POM che gestisce tutti i moduli
├── common-dto/                # Modulo con DTO condivisi
│   └── src/main/java/com/example/common/dto/
│       ├── UserDTO.java
│       ├── RoleDTO.java
│       ├── FunctionalityDTO.java
│       ├── UserRolesDTO.java
│       ├── RoleTableDTO.java
│       └── RoleFunctionalityTableDTO.java
├── entity/                    # Modulo con entità JPA e repository
│   └── src/main/java/com/example/entity/
│       ├── model/
│       │   ├── User.java
│       │   ├── Role.java
│       │   └── Functionality.java
│       └── repository/
│           ├── UserRepository.java
│           ├── RoleRepository.java
│           └── FunctionalityRepository.java
├── roles-service/             # Microservizio backend per gestione ruoli
│   ├── pom.xml               # Dipende da: entity, common-dto
│   ├── src/main/java/com/example/roles/
│   │   ├── RolesServiceApplication.java
│   │   ├── controller/
│   │   │   └── RoleController.java
│   │   └── config/
│   │       ├── SecurityConfig.java
│   │       ├── OpenApiConfig.java
│   │       └── DataLoader.java
│   ├── src/main/resources/
│   │   └── application.yml
│   └── start.sh
├── vaadin-app/               # Applicazione frontend Vaadin
│   ├── pom.xml              # Dipende da: common-dto
│   ├── src/main/java/com/example/vaadin/
│   │   ├── VaadinApplication.java
│   │   ├── MainView.java
│   │   ├── RoleView.java
│   │   ├── config/
│   │   │   └── SecurityConfig.java
│   │   └── service/
│   │       ├── RolesService.java
│   │       └── ActiveRoleService.java
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── frontend/
│   └── start.sh
├── docker/                   # Configurazione Keycloak
│   ├── docker-compose.yml
│   └── import-realm.json
└── backup.sh
```

## Vantaggi della Nuova Architettura

### 1. **Separazione delle Responsabilità**
- **common-dto**: DTOs condivisi tra tutti i moduli, garantisce consistenza
- **entity**: Logica di persistenza centralizzata, riutilizzabile
- **roles-service**: Business logic per gestione ruoli
- **vaadin-app**: UI separata dal backend

### 2. **Riutilizzabilità del Codice**
- Le entità e i repository possono essere riutilizzati da altri servizi
- I DTO comuni evitano duplicazione di codice
- Facilita la creazione di nuovi microservizi

### 3. **Gestione delle Dipendenze**
- Parent POM centralizza versioni di Spring Boot, Vaadin, ecc.
- Dependency Management condiviso
- Riduce conflitti di versione

### 4. **Manutenibilità**
- Modifiche alle entità in un solo posto
- DTO condivisi mantengono contratti API consistenti
- Facile aggiungere nuovi moduli

### 5. **Testabilità**
- Ogni modulo può essere testato indipendentemente
- Mock più semplici con moduli separati

## Build del Progetto

### Build completo (da root)
```bash
cd /workspace/db-ready/vaadin-prj
mvn clean install
```

### Build singolo modulo
```bash
cd /workspace/db-ready/vaadin-prj/common-dto
mvn clean install

cd /workspace/db-ready/vaadin-prj/entity
mvn clean install

cd /workspace/db-ready/vaadin-prj/roles-service
mvn clean install

cd /workspace/db-ready/vaadin-prj/vaadin-app
mvn clean install
```

## Avvio dell'Applicazione

### 1. Avvia Keycloak
```bash
cd /workspace/db-ready/vaadin-prj/docker
docker-compose up -d
```

### 2. Avvia Roles Service
```bash
cd /workspace/db-ready/vaadin-prj/roles-service
./start.sh
# oppure
mvn spring-boot:run
```

### 3. Avvia Vaadin App
```bash
cd /workspace/db-ready/vaadin-prj/vaadin-app
./start.sh
# oppure
mvn spring-boot:run
```

## Porte

- **Keycloak**: http://localhost:8081
- **Roles Service**: http://localhost:8091
  - API: http://localhost:8091/api/roles
  - Swagger UI: http://localhost:8091/swagger-ui.html
  - H2 Console: http://localhost:8091/h2-console
- **Vaadin App**: http://localhost:8080

## Credenziali di Test

### Keycloak Admin
- Username: `admin`
- Password: `admin`

### Utenti Test (realm: demo-realm)
- Username: `test` / Password: `test` (Ruoli: USER, MANAGER)
- Username: `admin` / Password: `admin` (Ruolo: ADMIN)
- Username: `manager` / Password: `manager` (Ruolo: MANAGER)
- Username: `guest` / Password: `guest` (Ruolo: GUEST)

## Dipendenze tra Moduli

```
vaadin-microservice-parent (pom.xml)
    │
    ├── common-dto (no dependencies)
    │
    ├── entity
    │   └── depends on: Spring Data JPA
    │
    ├── roles-service
    │   ├── depends on: entity
    │   ├── depends on: common-dto
    │   └── depends on: Spring Boot, Security, OAuth2 Resource Server
    │
    └── vaadin-app
        ├── depends on: common-dto
        └── depends on: Spring Boot, Vaadin, Security, OAuth2 Client
```

## Swagger/OpenAPI

Il roles-service espone la documentazione API tramite Swagger:
- URL: http://localhost:8091/swagger-ui.html
- Per testare gli endpoint, usa il pulsante "Authorize" e inserisci un token JWT valido

## Backup

Per eseguire il backup del progetto:
```bash
cd /workspace/db-ready/vaadin-prj
./backup.sh
```

## Tecnologie Utilizzate

- **Java 17**
- **Spring Boot 3.2.0** (aggiornato per supporto RestClient)
- **Spring Framework 6.1+** (RestClient API)
- **Vaadin 24.2.5**
- **Keycloak** (OAuth2/OIDC)
- **H2 Database** (in-memory per development)
- **MinIO** (Object Storage per file upload)
- **Maven** (build tool)
- **SpringDoc OpenAPI** (API documentation)

## Confronto con Architettura Precedente

### Prima (vaadin-microservice)
- DTO duplicati in entrambi i servizi
- Entità solo in roles-service
- Accoppiamento più forte

### Dopo (vaadin-prj)
- DTO condivisi nel modulo common-dto
- Entità e repository separati nel modulo entity
- Migliore separazione delle responsabilità
- Più facile aggiungere nuovi servizi
- Più manutenibile e testabile

## Streaming File Upload Architecture

### Panoramica
Il progetto implementa un sistema di upload file completamente streaming che consente di caricare file di qualsiasi dimensione (anche 30GB+) senza consumare memoria proporzionale alla dimensione del file.

### Stack HTTP Client Migrato a RestClient

A partire dalla versione Spring Boot 3.2.0, il progetto utilizza **RestClient** (Spring 6.1+) come client HTTP unificato, sostituendo sia WebClient (reattivo) che RestTemplate (legacy):

#### Vantaggi di RestClient
- **API moderna e fluente**: Sintassi più leggibile e intuitiva
- **Sincrono per natura**: Perfetto per applicazioni Vaadin UI (no `.block()` necessario)
- **Migliore supporto streaming**: Gestione nativa di InputStream e streaming upload
- **Logging integrato**: Interceptors per request/response logging con correlation ID
- **Performance**: Ottimizzato per operazioni sincrone senza overhead reattivo

```java
// Esempio di utilizzo RestClient
String[] files = restClient.get()
    .uri(rolesServiceUrl + "/api/files/list")
    .headers(headers -> {
        if (jwtToken != null) {
            headers.setBearerAuth(jwtToken);
        }
    })
    .retrieve()
    .body(String[].class);
```

### Flusso End-to-End dello Streaming

```
┌──────────────┐        ┌──────────────┐        ┌──────────────┐        ┌───────────┐
│   Browser    │  ════► │  Vaadin App  │  ════► │Roles Service │  ════► │   MinIO   │
│ (JavaScript) │        │ (RestClient) │        │ (InputStream)│        │  Storage  │
└──────────────┘        └──────────────┘        └──────────────┘        └───────────┘
     Fetch API         HttpServletRequest      Direct Streaming        Object Storage
```

### 1. Browser → Vaadin App (JavaScript Streaming Upload)

**File**: `vaadin-app/src/main/resources/META-INF/resources/js/simple-streaming-upload.js`

```javascript
// Upload con Fetch API - NO chunking, invio diretto del file
await fetch('/api/simple-upload/stream', {
    method: 'POST',
    headers: {
        'Authorization': 'Bearer ' + window.jwtToken,
        'Content-Type': 'application/octet-stream',
        'X-Filename': file.name,
        'X-Content-Type': file.type
    },
    body: file  // File inviato direttamente come body
});
```

**Caratteristiche:**
- Nessun chunking lato client
- Stream diretto del file via HTTP
- JWT token per autenticazione
- Headers custom per metadati (X-Filename, X-Content-Type)

### 2. Vaadin App Controller (HttpServletRequest → InputStream)

**File**: `vaadin-app/src/main/java/com/example/vaadin/controller/SimpleStreamingUploadController.java`

```java
@PostMapping(value = "/stream", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public Mono<Map<String, Object>> uploadStream(
        @RequestHeader("X-Filename") String filename,
        @RequestHeader("X-Content-Type") String contentType,
        HttpServletRequest request) {
    
    // Ottieni InputStream direttamente dalla richiesta HTTP
    InputStream inputStream = request.getInputStream();
    
    // Passa lo stream al service (NO buffering!)
    return fileStreamingService.uploadFileStreaming(filename, inputStream, contentType);
}
```

**Caratteristiche:**
- `HttpServletRequest.getInputStream()`: accesso diretto allo stream HTTP
- Nessun buffer in memoria
- InputStream passato direttamente al service layer

### 3. Vaadin App → Roles Service (RestClient Streaming)

**File**: `vaadin-app/src/main/java/com/example/vaadin/service/FileStreamingService.java`

```java
public Mono<Map<String, Object>> uploadFileStreaming(
        String fileName, 
        InputStream inputStream, 
        String contentType) {
    
    // Wrap con ProgressTrackingInputStream per log ogni 5MB
    ProgressTrackingInputStream progressStream = new ProgressTrackingInputStream(
        inputStream, 
        fileName,
        5 * 1024 * 1024  // Log ogni 5MB
    );
    
    InputStreamResource resource = new InputStreamResource(progressStream);
    
    // RestClient streaming upload
    Map<String, Object> result = restClient.post()
        .uri(rolesServiceUrl + "/api/files/upload-stream")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header("X-Filename", fileName)
        .header("X-Content-Type", contentType)
        .headers(headers -> {
            if (jwtToken != null) {
                headers.setBearerAuth(jwtToken);
            }
        })
        .body(resource)  // InputStream wrappato in InputStreamResource
        .retrieve()
        .body(Map.class);
}
```

**Caratteristiche:**
- **RestClient** per chiamate HTTP sincrone
- `InputStreamResource`: wrapper Spring per streaming InputStream
- `ProgressTrackingInputStream`: logging ogni 5MB con velocità e progresso
- JWT token propagato tramite Bearer authentication
- Nessun caricamento in memoria del file

### 4. Roles Service → MinIO (MinIO SDK Streaming)

**File**: `roles-service/src/main/java/com/example/roles/controller/FileStreamingController.java`

```java
@PostMapping(value = "/upload-stream", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseEntity<?> uploadStream(
        InputStream inputStream,
        @RequestHeader("X-Filename") String filename,
        @RequestHeader("X-Content-Type") String contentType) {
    
    // Stream direttamente a MinIO (NO temporary files!)
    String storedFileName = minioService.uploadFileStreaming(
        filename,      // Nome originale mantenuto
        inputStream,   // Stream passato direttamente
        contentType,
        -1            // Size sconosciuta (chunked transfer)
    );
}
```

**File**: `roles-service/src/main/java/com/example/roles/service/MinioStreamingService.java`

```java
public String uploadFileStreaming(String fileName, InputStream inputStream, 
                                   String contentType, long fileSize) {
    
    minioClient.putObject(
        PutObjectArgs.builder()
            .bucket(bucketName)
            .object(fileName)
            .stream(inputStream, -1, 10485760)  // -1 = unknown size, 10MB part size
            .contentType(contentType)
            .build()
    );
}
```

**Caratteristiche:**
- InputStream passato direttamente a MinIO SDK
- MinIO gestisce automaticamente multipart upload per file grandi
- Part size: 10MB (configurabile)
- Nessun file temporaneo su disco
- Nessun buffer in memoria

### Memory Footprint

```
File da 30GB:
┌─────────────────┬──────────────┬─────────────────┐
│ Componente      │ Memoria Usata│ Note            │
├─────────────────┼──────────────┼─────────────────┤
│ Browser         │ ~0 MB        │ Streaming API   │
│ Vaadin App      │ ~8 MB        │ Buffer I/O      │
│ Roles Service   │ ~10 MB       │ MinIO SDK       │
│ MinIO           │ ~10 MB/part  │ Multipart       │
├─────────────────┼──────────────┼─────────────────┤
│ TOTALE          │ ~28 MB       │ Costante!       │
└─────────────────┴──────────────┴─────────────────┘
```

### Progress Tracking

**ProgressTrackingInputStream** logga:
- Ogni 5MB caricati
- Velocità di upload (MB/s)
- Percentuale completamento
- Tempo trascorso

```
📊 STREAMING PROGRESS | File: large-video.mp4 | Uploaded: 150.00 MB | Speed: 12.50 MB/s | Duration: 12000 ms
📊 STREAMING PROGRESS | File: large-video.mp4 | Uploaded: 155.00 MB | Speed: 12.45 MB/s | Duration: 12450 ms
✅ STREAMING COMPLETE | File: large-video.mp4 | Total: 158.23 MB | Avg Speed: 12.48 MB/s | Total Duration: 12678 ms
```

### RestClient Configuration & Logging

**File**: `vaadin-app/src/main/java/com/example/vaadin/config/HttpClientConfig.java`

```java
@Bean
public RestClient.Builder restClientBuilder() {
    return RestClient.builder()
        .requestInterceptor(loggingInterceptor());
}

private ClientHttpRequestInterceptor loggingInterceptor() {
    return (request, body, execution) -> {
        long requestId = requestCounter.incrementAndGet();
        
        // Log request con correlation ID
        logger.info("║ RESTCLIENT REQUEST #{}", requestId);
        logger.info("║ Method: {}", request.getMethod());
        logger.info("║ URL: {}", request.getURI());
        
        var response = execution.execute(request, body);
        
        // Log response correlata
        logger.info("║ RESTCLIENT RESPONSE #{}", requestId);
        logger.info("║ Status: {}", response.getStatusCode());
        
        return response;
    };
}
```

### API Endpoints

#### Upload File
```bash
POST /api/simple-upload/stream
Content-Type: application/octet-stream
Authorization: Bearer <JWT_TOKEN>
X-Filename: example.pdf
X-Content-Type: application/pdf
Body: <file binary stream>
```

#### List Files
```bash
GET /api/files/list
Authorization: Bearer <JWT_TOKEN>
```

#### Get File Metadata
```bash
GET /api/files/metadata/{filename}
Authorization: Bearer <JWT_TOKEN>
```

#### Delete File
```bash
DELETE /api/files/{filename}
Authorization: Bearer <JWT_TOKEN>
```

### Avvio MinIO

```bash
cd /home/valerio/prj/vaadin-prj/minio
./start-minio.sh
```

MinIO Console: http://localhost:9001
- Username: `minioadmin`
- Password: `minioadmin`

### Vantaggi dell'Architettura

1. ✅ **Zero Memory Footprint**: Memoria costante (~28MB) per file di qualsiasi dimensione
2. ✅ **True Streaming**: Nessun buffer, file temporanei o caricamento in memoria
3. ✅ **RestClient Moderno**: API più semplice e performante di WebClient/RestTemplate
4. ✅ **Progress Tracking**: Monitoring in tempo reale senza overhead
5. ✅ **JWT Propagation**: Token passato automaticamente attraverso tutti i layer
6. ✅ **Nome File Originale**: Mantenuto durante tutto il processo
7. ✅ **Error Handling**: Gestione errori a ogni livello con logging dettagliato
8. ✅ **Scalabile**: Supporta file da pochi KB fino a 30GB+

### Confronto: WebClient vs RestClient

| Feature | WebClient (Prima) | RestClient (Ora) |
|---------|-------------------|------------------|
| API Style | Reattivo (Flux/Mono) | Sincrono/Fluente |
| `.block()` necessario | ✅ Sì | ❌ No |
| Streaming InputStream | Complesso (conversione Flux) | Nativo (InputStreamResource) |
| Performance UI | Overhead reattivo | Ottimizzato sincrono |
| Codice boilerplate | Alto | Basso |
| Logging | ExchangeFilterFunction | ClientHttpRequestInterceptor |
| Manutenibilità | Media | Alta |

## Sviluppo Futuro

Con questa architettura modulare è facile aggiungere:
- Nuovi microservizi che riutilizzano entity e common-dto
- Modulo di validazione condiviso
- Modulo di utility comuni
- Modulo di sicurezza condiviso
- Client REST generati automaticamente dai DTO
- Resume upload per file interrotti
- Compressione on-the-fly durante lo streaming
- Thumbnail generation per immagini/video
- Virus scanning integrato durante l'upload
