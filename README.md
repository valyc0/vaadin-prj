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
- **Spring Boot 3.1.5**
- **Vaadin 24.2.5**
- **Keycloak** (OAuth2/OIDC)
- **H2 Database** (in-memory per development)
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

## Sviluppo Futuro

Con questa architettura modulare è facile aggiungere:
- Nuovi microservizi che riutilizzano entity e common-dto
- Modulo di validazione condiviso
- Modulo di utility comuni
- Modulo di sicurezza condiviso
- Client REST generati automaticamente dai DTO
