#!/bin/bash

# Script di test per l'upload streaming su MinIO
# Uso: ./test-upload.sh [dimensione_MB] [token_jwt]

set -e

# Colori per output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parametri
FILE_SIZE_MB=${1:-100}  # Default 100MB se non specificato
JWT_TOKEN=${2:-""}
SERVICE_URL="http://localhost:8091/api/files"

echo -e "${BLUE}=== Test Upload Streaming MinIO ===${NC}"
echo ""

# Verifica se il servizio è attivo
echo -e "${YELLOW}1. Verifico che il servizio sia attivo...${NC}"
if ! curl -s -o /dev/null -w "%{http_code}" "$SERVICE_URL/list" | grep -q "401\|200"; then
    echo -e "${RED}Errore: Il servizio non sembra essere attivo su $SERVICE_URL${NC}"
    echo "Avvia il servizio con: cd /home/valerio/prj/vaadin-prj/roles-service && ./start.sh"
    exit 1
fi
echo -e "${GREEN}✓ Servizio attivo${NC}"
echo ""

# Verifica MinIO
echo -e "${YELLOW}2. Verifico che MinIO sia attivo...${NC}"
if ! curl -s -o /dev/null http://localhost:9000; then
    echo -e "${RED}Errore: MinIO non sembra essere attivo su http://localhost:9000${NC}"
    echo "Avvia MinIO con: cd /home/valerio/prj/vaadin-prj/minio && ./start-minio.sh"
    exit 1
fi
echo -e "${GREEN}✓ MinIO attivo${NC}"
echo ""

# Crea file di test temporaneo
TEST_FILE="/tmp/test_upload_${FILE_SIZE_MB}MB_$(date +%s).bin"
echo -e "${YELLOW}3. Creo file di test da ${FILE_SIZE_MB}MB...${NC}"
dd if=/dev/urandom of="$TEST_FILE" bs=1M count=$FILE_SIZE_MB 2>/dev/null
FILE_SIZE=$(stat -c%s "$TEST_FILE")
echo -e "${GREEN}✓ File creato: $TEST_FILE ($(numfmt --to=iec-i --suffix=B $FILE_SIZE))${NC}"
echo ""

# Upload del file
echo -e "${YELLOW}4. Inizio upload streaming...${NC}"
START_TIME=$(date +%s)

if [ -z "$JWT_TOKEN" ]; then
    echo -e "${BLUE}Nota: Upload senza autenticazione (potrebbe fallire se richiesta)${NC}"
    RESPONSE=$(curl -s -X POST "$SERVICE_URL/upload" \
        -F "file=@$TEST_FILE" \
        -w "\nHTTP_STATUS:%{http_code}")
else
    RESPONSE=$(curl -s -X POST "$SERVICE_URL/upload" \
        -H "Authorization: Bearer $JWT_TOKEN" \
        -F "file=@$TEST_FILE" \
        -w "\nHTTP_STATUS:%{http_code}")
fi

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# Estrai status code
HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)
BODY=$(echo "$RESPONSE" | sed '/HTTP_STATUS/d')

echo ""
if [ "$HTTP_STATUS" = "200" ]; then
    echo -e "${GREEN}✓ Upload completato con successo!${NC}"
    echo ""
    echo -e "${BLUE}Dettagli upload:${NC}"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
    echo ""
    echo -e "${BLUE}Statistiche:${NC}"
    echo "  - Durata: ${DURATION}s"
    THROUGHPUT=$(echo "scale=2; $FILE_SIZE_MB / $DURATION" | bc)
    echo "  - Throughput: ${THROUGHPUT} MB/s"
    
    # Estrai nome file salvato
    STORED_FILE=$(echo "$BODY" | grep -o '"storedFileName":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$STORED_FILE" ]; then
        echo ""
        echo -e "${BLUE}File caricato: ${GREEN}$STORED_FILE${NC}"
        echo ""
        echo -e "${YELLOW}5. Verifico che il file esista su MinIO...${NC}"
        
        if [ -z "$JWT_TOKEN" ]; then
            METADATA=$(curl -s "$SERVICE_URL/metadata/$STORED_FILE")
        else
            METADATA=$(curl -s -H "Authorization: Bearer $JWT_TOKEN" "$SERVICE_URL/metadata/$STORED_FILE")
        fi
        
        if echo "$METADATA" | grep -q "filename"; then
            echo -e "${GREEN}✓ File verificato su MinIO${NC}"
            echo "$METADATA" | python3 -m json.tool 2>/dev/null || echo "$METADATA"
        else
            echo -e "${RED}✗ Impossibile verificare il file${NC}"
        fi
    fi
else
    echo -e "${RED}✗ Upload fallito (HTTP $HTTP_STATUS)${NC}"
    echo "$BODY"
fi

# Cleanup
echo ""
echo -e "${YELLOW}6. Pulizia file temporaneo...${NC}"
rm -f "$TEST_FILE"
echo -e "${GREEN}✓ File temporaneo eliminato${NC}"

echo ""
echo -e "${BLUE}=== Test completato ===${NC}"

exit 0
