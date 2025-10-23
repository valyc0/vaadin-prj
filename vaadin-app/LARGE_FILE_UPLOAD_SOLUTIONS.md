# 🚀 Soluzioni per Upload di File Grandi da Browser con Vaadin

## 🔴 Problema: Limitazioni del Browser

Il browser **deve caricare il file in memoria** prima di inviarlo, creando questi limiti:
- **File < 500MB**: OK, funziona bene
- **File 500MB-2GB**: Possibile ma lento e usa molta RAM browser
- **File > 2GB**: **IMPOSSIBILE** - browser crash o timeout

## ✅ 3 Soluzioni Concrete

---

## Soluzione 1: **Chunked Upload** (RACCOMANDATA) ⭐

### 📋 Caratteristiche
- ✅ File split in chunk da 10MB nel browser
- ✅ Solo UN chunk in memoria alla volta
- ✅ Nessun limite di dimensione (50GB+)
- ✅ Progress tracking per chunk
- ✅ Possibilità di pause/resume (con modifica)

### 🎯 Come Funziona

```
1. Browser: File.slice() → Legge solo 10MB alla volta
2. JavaScript: Fetch API → Invia chunk al server
3. Server: Salva chunk temporaneo in /tmp
4. Ripeti per tutti i chunk
5. Server: Assembla chunk + stream a MinIO
6. Cleanup: Elimina file temporanei
```

### 📝 Implementazione

**Frontend: `ChunkedFileUploadView.java`** (già creato)
- View Vaadin con JavaScript inline
- Upload chunk via Fetch API
- Progress tracking visivo

**Backend: `ChunkedUploadController.java`** (già creato)
- `POST /api/files/upload-chunk` - Riceve singolo chunk
- `POST /api/files/finalize-upload` - Assembla e carica su MinIO

### 🚀 Uso

1. Avvia roles-service:
   ```bash
   cd /home/valerio/prj/vaadin-prj/roles-service
   ./start.sh
   ```

2. Avvia vaadin-app:
   ```bash
   cd /home/valerio/prj/vaadin-prj/vaadin-app
   ./start.sh
   ```

3. Naviga a: `http://localhost:8080/chunked-upload`

4. Seleziona file e clicca "Start Upload"

### 💾 Utilizzo Memoria

| Componente | Memoria Usata |
|-----------|---------------|
| Browser | ~10MB (un chunk) |
| Vaadin Server | ~10MB (un chunk) |
| Roles Service | ~10MB + temp chunks su disco |
| MinIO | Streaming diretto |

**Total RAM: ~30MB costanti, indipendentemente dalla dimensione del file!**

### ⚠️ Considerazioni

- **Pro**: Nessun limite dimensione, efficiente, robusto
- **Pro**: Può gestire file 50GB+ senza problemi
- **Con**: Usa spazio disco temporaneo (/tmp)
- **Con**: Richiede cleanup se upload fallisce

---

## Soluzione 2: **StreamingReceiver** (Attuale)

### 📋 Caratteristiche
- ✅ Streaming lato server (Vaadin → MinIO)
- ✅ Zero buffering server
- ❌ Browser carica file completo in memoria

### 🎯 Quando Usarla
- File < 500MB
- Upload occasionali
- Semplicità di implementazione

### 📝 Uso
Già implementato in `FileUploadView.java`

Naviga a: `http://localhost:8080/file-upload`

### 💾 Utilizzo Memoria

| Componente | Memoria Usata (file 1GB) |
|-----------|--------------------------|
| Browser | **1GB** ⚠️ |
| Vaadin Server | ~8KB |
| Roles Service | ~10MB |

**Limite pratico: ~1-2GB** (dipende dal browser e RAM disponibile)

---

## Soluzione 3: **Direct REST API Call** (JavaScript puro)

### 📋 Caratteristiche
- ✅ Bypassa componente Upload Vaadin
- ✅ Chiamata diretta da JavaScript a roles-service
- ✅ Usa Fetch API con streaming
- ❌ Browser carica file completo

### 📝 Implementazione

```javascript
// In una view Vaadin, aggiungi questo JavaScript
async function uploadFileDirect(file) {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await fetch('http://localhost:8091/api/files/upload', {
        method: 'POST',
        body: formData
    });
    
    if (response.ok) {
        const result = await response.json();
        console.log('Upload completed:', result);
    }
}

// Uso
document.getElementById('file-input').addEventListener('change', (e) => {
    const file = e.target.files[0];
    uploadFileDirect(file);
});
```

### 💾 Utilizzo Memoria
Simile a Soluzione 2, browser carica file completo.

**Limite pratico: ~1-2GB**

---

## 📊 Confronto Soluzioni

| Soluzione | Max File Size | Memoria Browser | Memoria Server | Complessità | Raccomandato |
|-----------|---------------|-----------------|----------------|-------------|--------------|
| **Chunked Upload** | **50GB+** | **10MB** | 10MB + temp disk | Media | ⭐⭐⭐⭐⭐ |
| **StreamingReceiver** | ~2GB | File completo | 8KB | Bassa | ⭐⭐⭐ |
| **Direct REST** | ~2GB | File completo | 10MB | Bassa | ⭐⭐ |

---

## 🎯 Raccomandazioni

### Per File < 500MB
Usa **StreamingReceiver** (Soluzione 2)
- Semplice, già implementato
- Funziona bene per la maggior parte dei casi

### Per File 500MB - 10GB
Usa **Chunked Upload** (Soluzione 1)
- Evita problemi di memoria browser
- Upload più stabile
- Progress tracking migliore

### Per File > 10GB
Usa **Chunked Upload** (Soluzione 1) o considera:
- **CLI tool** (curl, custom client)
- **Desktop application** (JavaFX, Electron)
- **FTP/SFTP** per file massivi

---

## 🔧 Configurazione Chunked Upload

### Dimensione Chunk
Default: 10MB. Modifica in `ChunkedFileUploadView.java`:

```javascript
const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB chunks (per connessioni lente)
// oppure
const CHUNK_SIZE = 50 * 1024 * 1024; // 50MB chunks (per connessioni veloci)
```

### Spazio Disco Temporaneo
Default: `/tmp/uploads`

Modifica in `ChunkedUploadController.java`:
```java
private static final String TEMP_UPLOAD_DIR = "/var/uploads/temp";
```

**Nota**: Assicurati che la directory abbia spazio sufficiente!

### Cleanup Automatico
Implementa un job per pulire upload incompleti > 24h:

```java
@Scheduled(cron = "0 0 2 * * ?") // Ogni giorno alle 2am
public void cleanupOldUploads() {
    Path uploadsDir = Paths.get(TEMP_UPLOAD_DIR);
    Files.list(uploadsDir)
        .filter(path -> {
            try {
                FileTime modifiedTime = Files.getLastModifiedTime(path);
                return Duration.between(modifiedTime.toInstant(), Instant.now())
                    .toHours() > 24;
            } catch (IOException e) {
                return false;
            }
        })
        .forEach(path -> deleteDirectory(path));
}
```

---

## 🧪 Test

### Test Chunked Upload con File Grande

```bash
# Crea file di test da 5GB
dd if=/dev/zero of=/tmp/test5gb.bin bs=1M count=5120

# Usa browser per upload via http://localhost:8080/chunked-upload
```

### Monitoraggio

```bash
# Monitora spazio disco temp
watch -n 1 "du -sh /tmp/uploads/*"

# Monitora memoria server
jconsole # Collega al processo Java
```

---

## 🎓 Conclusioni

Per **file grandi (>1GB)** con Vaadin:
1. ✅ **Usa Chunked Upload** - È l'unica soluzione scalabile
2. ✅ Testa con file reali del tuo caso d'uso
3. ✅ Configura cleanup automatico
4. ✅ Monitora spazio disco /tmp

Il browser **non può** fare vero streaming come curl, ma con chunking ottieni risultati equivalenti! 🚀
