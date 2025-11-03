#!/bin/bash

# Script per testare l'upload e verificare il comportamento

echo "======================================================================"
echo "FILE UPLOAD TEST SCRIPT"
echo "======================================================================"
echo ""

# Configurazione
FILE_TO_UPLOAD="${1:-/tmp/test-file.bin}"
FILE_SIZE="${2:-100M}"  # Default 100MB
URL="http://localhost:8080/api/streaming/simple-upload"

echo "Configurazione:"
echo "  File da caricare: $FILE_TO_UPLOAD"
echo "  Dimensione: $FILE_SIZE"
echo "  URL: $URL"
echo ""

# Crea il file di test se non esiste
if [ ! -f "$FILE_TO_UPLOAD" ]; then
    echo "📝 Creazione file di test..."
    dd if=/dev/zero of="$FILE_TO_UPLOAD" bs=1M count=${FILE_SIZE%M} status=progress 2>&1 | tail -1
    echo "✓ File creato: $(ls -lh $FILE_TO_UPLOAD | awk '{print $5}')"
fi

echo ""
echo "======================================================================"
echo "PRIMA DELL'UPLOAD - Verifica memoria iniziale"
echo "======================================================================"

# PID del processo
PID=$(ps aux | grep 'vaadin-app' | grep -v grep | awk '{print $2}' | head -1)

if [ -z "$PID" ]; then
    echo "❌ Processo vaadin-app non trovato!"
    exit 1
fi

# Memoria iniziale
RSS_BEFORE=$(ps -p $PID -o rss= | awk '{print $1/1024}')
echo "Memoria RSS: ${RSS_BEFORE} MB"

if command -v jstat &> /dev/null; then
    HEAP_BEFORE=$(jstat -gc $PID 2>/dev/null | tail -1 | awk '{used=($3+$4+$6+$8)/1024; printf "%.2f", used}')
    echo "Heap usato: ${HEAP_BEFORE} MB"
fi

echo ""
echo "======================================================================"
echo "UPLOAD IN CORSO"
echo "======================================================================"
echo ""

# Avvia l'upload
echo "🚀 Caricamento file..."
START_TIME=$(date +%s)

curl -X POST "$URL" \
    -F "file=@$FILE_TO_UPLOAD" \
    -H "Content-Type: multipart/form-data" \
    -w "\n\nHTTP Status: %{http_code}\nTime: %{time_total}s\nSpeed: %{speed_upload} bytes/s\n" \
    -o /tmp/upload-response.json

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "✓ Upload completato in $DURATION secondi"
echo ""
echo "Risposta:"
cat /tmp/upload-response.json | jq '.' 2>/dev/null || cat /tmp/upload-response.json
echo ""

echo ""
echo "======================================================================"
echo "DOPO L'UPLOAD - Verifica memoria finale"
echo "======================================================================"

# Aspetta qualche secondo per il GC
sleep 2

# Memoria finale
RSS_AFTER=$(ps -p $PID -o rss= | awk '{print $1/1024}')
RSS_DIFF=$(echo "$RSS_AFTER - $RSS_BEFORE" | bc)

echo "Memoria RSS: ${RSS_AFTER} MB (delta: ${RSS_DIFF} MB)"

if command -v jstat &> /dev/null; then
    HEAP_AFTER=$(jstat -gc $PID 2>/dev/null | tail -1 | awk '{used=($3+$4+$6+$8)/1024; printf "%.2f", used}')
    HEAP_DIFF=$(echo "$HEAP_AFTER - $HEAP_BEFORE" | bc)
    echo "Heap usato: ${HEAP_AFTER} MB (delta: ${HEAP_DIFF} MB)"
fi

echo ""
echo "======================================================================"
echo "ANALISI RISULTATI"
echo "======================================================================"

FILE_SIZE_MB=$(stat -f%z "$FILE_TO_UPLOAD" 2>/dev/null || stat -c%s "$FILE_TO_UPLOAD" | awk '{print $1/1024/1024}')

echo "File size: ${FILE_SIZE_MB} MB"
echo "Memoria incremento RSS: ${RSS_DIFF} MB"

if command -v bc &> /dev/null; then
    RATIO=$(echo "scale=2; $RSS_DIFF / $FILE_SIZE_MB" | bc)
    echo "Ratio (memoria/file): ${RATIO}x"
    
    if (( $(echo "$RATIO < 0.5" | bc -l) )); then
        echo ""
        echo "✅ STREAMING VERIFICATO!"
        echo "   La memoria usata è molto inferiore alla dimensione del file."
        echo "   Il file è stato streamato correttamente senza bufferizzazione."
    else
        echo ""
        echo "⚠️  POSSIBILE BUFFERIZZAZIONE"
        echo "   La memoria usata è comparabile alla dimensione del file."
        echo "   Potrebbero esserci problemi di bufferizzazione."
    fi
fi

# Cleanup
rm -f /tmp/upload-response.json
