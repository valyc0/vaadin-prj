# Architettura Modulare - Vaadin Microservice

## Panoramica

Progetto ristrutturato con architettura **Maven multi-modulo** per migliorare:
- Separazione delle responsabilità
- Riutilizzabilità del codice
- Manutenibilità
- Testabilità
- Scalabilità

## Struttura Modulare

### 📦 Parent Module (`vaadin-microservice-parent`)

**File**: `/pom.xml`

**Responsabilità**:
- Gestisce versioni centralizzate di tutte le dipendenze
- Definisce i plugin Maven comuni
- Dichiara i moduli figlio
- Dependency Management condiviso

**Tecnologie**:
- Spring Boot 3.1.5 (parent)
- Vaadin 24.2.5
- Java 17

---

### 📦 Common DTO Module (`common-dto`)

**Path**: `/common-dto`

**Responsabilità**:
- Contiene tutti i Data Transfer Objects condivisi
- Utilizzato sia da `roles-service` che da `vaadin-app`
- Garantisce consistenza nei contratti API

**Classi**:
```
com.example.common.dto
├── UserDTO.java                      // Dati utente base
├── RoleDTO.java                      // Ruolo con funzionalità
├── FunctionalityDTO.java             // Singola funzionalità
├── UserRolesDTO.java                 // Utente con lista ruoli
├── RoleTableDTO.java                 // DTO per visualizzazione tabella
└── RoleFunctionalityTableDTO.java    // DTO per tabella ruoli-funzionalità
```

**Dipendenze**: Nessuna (solo Java standard)

**Note**: Tutti i DTO implementano `Serializable` per supportare caching e sessioni distribuite.

---

### 📦 Entity Module (`entity`)

**Path**: `/entity`

**Responsabilità**:
- Contiene entità JPA del dominio
- Definisce i repository Spring Data JPA
- Logica di persistenza centralizzata

**Struttura**:
```
com.example.entity
├── model/
│   ├── User.java           // @Entity - Utente
│   ├── Role.java           // @Entity - Ruolo
│   └── Functionality.java  // @Entity - Funzionalità
└── repository/
    ├── UserRepository.java           // JPA Repository per User
    ├── RoleRepository.java           // JPA Repository per Role
    └── FunctionalityRepository.java  // JPA Repository per Functionality
```

**Relazioni JPA**:
- `User` ↔ `Role`: Many-to-Many (bidirezionale)
- `Role` ↔ `Functionality`: Many-to-Many (bidirezionale)

**Query Personalizzate**:
- `UserRepository.findByUsernameWithRolesAndFunctionalities()`: Eager fetch di ruoli e funzionalità
- `RoleRepository.findAllWithFunctionalities()`: Tutti i ruoli con funzionalità
- `RoleRepository.findByNameInWithFunctionalities()`: Ruoli specifici con funzionalità

**Dipendenze**:
- Spring Data JPA
- Jakarta Persistence API

---

### 📦 Roles Service Module (`roles-service`)

**Path**: `/roles-service`

**Responsabilità**:
- Microservizio REST per gestione ruoli e funzionalità
- OAuth2 Resource Server (valida JWT da Keycloak)
- Espone API RESTful documentate con Swagger
- Inizializza dati di test all'avvio

**Struttura**:
```
com.example.roles
├── RolesServiceApplication.java  // Main class
│   └── @EntityScan("com.example.entity.model")
│   └── @EnableJpaRepositories("com.example.entity.repository")
│
├── controller/
│   └── RoleController.java       // REST Controller
│       ├── GET /api/roles             // Ruoli utente autenticato
│       ├── GET /api/roles/all         // Tutti i ruoli
│       └── GET /api/roles/info        // Info token JWT
│
└── config/
    ├── SecurityConfig.java        // OAuth2 Resource Server config
    ├── OpenApiConfig.java         // Swagger/OpenAPI config
    └── DataLoader.java            // Inizializzazione dati test
```

**Dipendenze**:
- `entity` (modulo interno)
- `common-dto` (modulo interno)
- Spring Boot Starter Web
- Spring Boot Starter Security
- Spring Boot Starter OAuth2 Resource Server
- Spring Boot Starter Data JPA
- H2 Database
- SpringDoc OpenAPI

**Configurazione**:
- Porta: 8091
- Database: H2 in-memory
- JWT Validation: Keycloak (jwk-set-uri)

**API Endpoints**:
1. **GET /api/roles**
   - Descrizione: Ottiene ruoli e funzionalità dell'utente autenticato
   - Auth: Bearer JWT token
   - Response: `UserRolesDTO`

2. **GET /api/roles/all**
   - Descrizione: Ottiene tutti i ruoli disponibili
   - Auth: Bearer JWT token
   - Response: `List<RoleDTO>`

3. **GET /api/roles/info**
   - Descrizione: Mostra claims del JWT per debugging
   - Auth: Bearer JWT token
   - Response: JWT Claims (JSON)

**Dati di Test Inizializzati**:
- **Utenti**: test, admin, manager, guest
- **Ruoli**: GUEST, USER, MANAGER, ADMIN
- **Funzionalità**: 12 funzionalità in 3 categorie (USER_MANAGEMENT, REPORTING, ADMINISTRATION, DATA_ACCESS)

---

### 📦 Vaadin App Module (`vaadin-app`)

**Path**: `/vaadin-app`

**Responsabilità**:
- Frontend web con Vaadin Flow
- OAuth2 Client (login con Keycloak)
- Chiama il roles-service per ottenere ruoli
- Gestisce sessioni utente e ruolo attivo

**Struttura**:
```
com.example.vaadin
├── VaadinApplication.java     // Main class
│
├── MainView.java              // Vista conferma ruolo selezionato
│   └── Route: /role-confirmation
│
├── RoleView.java              // Vista principale con grid ruoli
│   └── Route: /
│
├── config/
│   └── SecurityConfig.java    // OAuth2 Client config
│       └── extends VaadinWebSecurity
│
└── service/
    ├── RolesService.java           // Client HTTP per roles-service
    │   └── Usa RestTemplate con Bearer token
    │
    └── ActiveRoleService.java      // Gestisce ruolo attivo in sessione
        └── Usa VaadinSession attributes
```

**Dipendenze**:
- `common-dto` (modulo interno)
- Vaadin Spring Boot Starter
- Spring Boot Starter OAuth2 Client
- Spring Boot Starter Security
- Spring Boot Starter Web

**Configurazione**:
- Porta: 8080
- OAuth2 Provider: Keycloak
- Client ID: vaadin-client
- Roles Service URL: http://localhost:8091/api/roles

**Flusso Autenticazione**:
1. Utente accede a http://localhost:8080
2. Redirect a Keycloak per login
3. Dopo login, ritorno con authorization code
4. Exchange code per access token
5. Token salvato in session OAuth2AuthorizedClient
6. Token usato per chiamare roles-service

**Views**:

1. **RoleView** (`/`)
   - Mostra informazioni utente autenticato
   - Grid con ruoli e funzionalità
   - Pulsante per selezionare ruolo attivo
   - Logout

2. **MainView** (`/role-confirmation`)
   - Conferma selezione ruolo
   - Mostra dettagli ruolo selezionato
   - Salva ruolo attivo in sessione Vaadin

---

## Flusso di Comunicazione

```
┌─────────────┐
│   Browser   │
└──────┬──────┘
       │ HTTP
       ▼
┌─────────────────┐
│  Vaadin App     │ :8080
│  (OAuth Client) │
└────────┬────────┘
         │
         ├─────────────────┐
         │                 │
         ▼                 ▼
   ┌──────────┐      ┌─────────────┐
   │ Keycloak │      │ Roles       │ :8091
   │          │      │ Service     │
   │  :8081   │      │ (Resource   │
   └──────────┘      │  Server)    │
                     └──────┬──────┘
                            │
                            ▼
                     ┌─────────────┐
                     │  Entity     │
                     │  Module     │
                     │  (JPA)      │
                     └──────┬──────┘
                            │
                            ▼
                     ┌─────────────┐
                     │ H2 Database │
                     └─────────────┘
```

## Vantaggi Architetturali

### 1. **Single Responsibility Principle**
Ogni modulo ha una responsabilità ben definita:
- `common-dto`: Solo DTOs
- `entity`: Solo persistenza
- `roles-service`: Solo business logic backend
- `vaadin-app`: Solo UI

### 2. **DRY (Don't Repeat Yourself)**
- DTO condivisi eliminano duplicazione
- Entity e repository usati solo una volta
- Configurazioni centralizzate nel parent POM

### 3. **Dependency Inversion**
- Moduli dipendono da astrazioni (interfaces)
- `roles-service` dipende da `entity` ma non da `vaadin-app`
- `vaadin-app` dipende da `common-dto` ma non da `entity`

### 4. **Scalabilità**
Facile aggiungere nuovi moduli:
- Nuovo microservizio che riutilizza `entity`
- Nuovo client (mobile, CLI) che usa `common-dto`
- Modulo di utility condiviso

### 5. **Testabilità**
- Test unitari per ogni modulo isolato
- Mock semplificati
- Test di integrazione più mirati

### 6. **Build Incrementale**
Maven compila solo i moduli modificati:
```bash
# Build solo common-dto
mvn install -pl common-dto

# Build roles-service e dipendenze
mvn install -pl roles-service -am

# Build tutto
mvn install
```

## Dipendenze tra Moduli

```
       Parent POM
           │
    ┌──────┼──────┬──────┐
    │      │      │      │
    ▼      ▼      ▼      ▼
common  entity  roles  vaadin
  dto            │      │
    │            │      │
    └────────────┼──────┘
                 │
                 ▼
            common-dto
                 │
                 └─ (nessuna dipendenza esterna)
```

**Regole**:
- `common-dto`: Nessuna dipendenza
- `entity`: Dipende solo da Spring Data JPA
- `roles-service`: Dipende da `entity` + `common-dto`
- `vaadin-app`: Dipende solo da `common-dto`

## Build e Deploy

### Build Completo
```bash
cd /workspace/db-ready/vaadin-prj
mvn clean install
```

### Build Ordine Corretto (gestito automaticamente da Maven)
1. `common-dto` (no dependencies)
2. `entity` (no module dependencies)
3. `roles-service` (depends on 1, 2)
4. `vaadin-app` (depends on 1)

### Packaging
Ogni servizio produce un JAR eseguibile:
- `roles-service/target/roles-service-1.0.0.jar`
- `vaadin-app/target/vaadin-app-1.0.0.jar`

## Evoluzione Futura

### Possibili Estensioni

1. **Modulo `common-security`**
   ```
   common-security/
   └── JWT validation utilities
   └── Common security configurations
   ```

2. **Modulo `common-exception`**
   ```
   common-exception/
   └── Custom exceptions
   └── Error handling utilities
   ```

3. **Nuovo Microservizio `audit-service`**
   ```
   audit-service/
   ├── depends on: entity, common-dto
   └── Tracks user actions
   ```

4. **API Gateway**
   ```
   api-gateway/
   └── Routes requests to microservices
   └── Centralized authentication
   ```

## Confronto con Architettura Precedente

| Aspetto | Vecchia | Nuova |
|---------|---------|-------|
| DTO | Duplicati in ogni servizio | Condivisi in `common-dto` |
| Entity | Solo in roles-service | Modulo separato `entity` |
| Riutilizzo | Difficile | Facile aggiungere servizi |
| Manutenzione | Modifiche in più posti | Modifiche centralizzate |
| Testing | Complesso | Moduli testabili separatamente |
| Build | Tutto insieme | Incrementale per modulo |
| Dipendenze | Versioni duplicate | Gestite dal parent POM |

## Best Practices Implementate

✅ Separazione delle responsabilità  
✅ Dependency Management centralizzato  
✅ Naming conventions consistenti  
✅ Documentazione API (Swagger)  
✅ Configuration externalizzata (application.yml)  
✅ Security by default (OAuth2)  
✅ Database migrations ready (H2 → PostgreSQL facile)  
✅ Logging strutturato  
✅ Health checks ready (/actuator/health)
