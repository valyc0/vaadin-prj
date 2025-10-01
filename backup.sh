#!/bin/bash

# Script di backup per il progetto Vaadin Demo
# Genera un tar.gz con i file principali escludendo i file temporanei e di build

# Configurazione
PROJECT_DIR="/workspace/db-ready/vaadin-microservice"
BACKUP_DIR="/workspace/db-ready/backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_NAME="vaadin-demo_backup_${TIMESTAMP}.tar.gz"

# Colori per output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=== Script di Backup Vaadin Demo ===${NC}"
echo "Progetto: $PROJECT_DIR"
echo "Backup: $BACKUP_DIR/$BACKUP_NAME"
echo ""

# Verifica che la directory del progetto esista
if [ ! -d "$PROJECT_DIR" ]; then
    echo -e "${RED}Errore: Directory del progetto non trovata: $PROJECT_DIR${NC}"
    exit 1
fi

# Crea la directory di backup se non esiste
mkdir -p "$BACKUP_DIR"

# Cambia nella directory del progetto
cd "$PROJECT_DIR" || exit 1

echo -e "${YELLOW}Creazione backup in corso...${NC}"

# Crea il tar.gz escludendo i file non necessari
tar -czf "$BACKUP_DIR/$BACKUP_NAME" \
    --exclude='target' \
    --exclude='node_modules' \
    --exclude='.git' \
    --exclude='.gitignore' \
    --exclude='.DS_Store' \
    --exclude='build' \
    --exclude='dist' \
    --exclude='.idea' \
    --exclude='.vscode' \
    --exclude='*.log' \
    --exclude='*.tmp' \
    --exclude='*~' \
    --exclude='roles-service/target' \
    --exclude='vaadin-app/target' \
    --exclude='roles-service/node_modules' \
    --exclude='vaadin-app/node_modules' \
    .

# Verifica che il backup sia stato creato
if [ -f "$BACKUP_DIR/$BACKUP_NAME" ]; then
    BACKUP_SIZE=$(du -h "$BACKUP_DIR/$BACKUP_NAME" | cut -f1)
    echo -e "${GREEN}✓ Backup creato con successo!${NC}"
    echo "  File: $BACKUP_NAME"
    echo "  Dimensione: $BACKUP_SIZE"
    echo "  Percorso: $BACKUP_DIR/$BACKUP_NAME"
    
    # Mostra il contenuto del backup
    echo ""
    echo -e "${YELLOW}Contenuto del backup:${NC}"
    tar -tzf "$BACKUP_DIR/$BACKUP_NAME" | head -20
    
    TOTAL_FILES=$(tar -tzf "$BACKUP_DIR/$BACKUP_NAME" | wc -l)
    if [ "$TOTAL_FILES" -gt 20 ]; then
        echo "... e altri $((TOTAL_FILES - 20)) file"
    fi
    echo ""
    echo -e "${GREEN}Backup completato! Totale file: $TOTAL_FILES${NC}"
    
else
    echo -e "${RED}✗ Errore durante la creazione del backup${NC}"
    exit 1
fi

# Lista degli ultimi 5 backup
echo ""
echo -e "${YELLOW}Ultimi backup disponibili:${NC}"
ls -lht "$BACKUP_DIR"/vaadin-demo_backup_*.tar.gz 2>/dev/null | head -5 || echo "Nessun backup precedente trovato"