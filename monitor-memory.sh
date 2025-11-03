#!/bin/bash

# Script per monitorare l'uso della memoria durante l'upload di file

echo "======================================================================"
echo "MEMORY MONITORING SCRIPT"
echo "======================================================================"
echo ""

# Trova il PID del processo Java (vaadin-app)
PID=$(ps aux | grep 'vaadin-app' | grep -v grep | awk '{print $2}' | head -1)

if [ -z "$PID" ]; then
    echo "❌ Processo vaadin-app non trovato!"
    echo "Assicurati che l'applicazione sia in esecuzione"
    exit 1
fi

echo "✓ Processo trovato: PID=$PID"
echo ""
echo "Monitoraggio in corso... (premi Ctrl+C per fermare)"
echo ""
echo "TIMESTAMP           | RSS (MB) | HEAP (MB) | CPU% | THREADS"
echo "-------------------------------------------------------------------"

# Monitoraggio continuo
while true; do
    # Timestamp
    TIMESTAMP=$(date '+%H:%M:%S')
    
    # RSS Memory (Resident Set Size) in MB
    RSS=$(ps -p $PID -o rss= | awk '{print $1/1024}')
    
    # CPU Usage
    CPU=$(ps -p $PID -o %cpu= | awk '{print $1}')
    
    # Number of threads
    THREADS=$(ps -p $PID -o nlwp= | awk '{print $1}')
    
    # Heap memory da jstat (se disponibile)
    if command -v jstat &> /dev/null; then
        HEAP=$(jstat -gc $PID 2>/dev/null | tail -1 | awk '{used=($3+$4+$6+$8)/1024; print used}')
        if [ -z "$HEAP" ]; then
            HEAP="N/A"
        else
            HEAP=$(printf "%.2f" $HEAP)
        fi
    else
        HEAP="N/A"
    fi
    
    # Print stats
    printf "%s | %8.2f | %9s | %5s | %7s\n" "$TIMESTAMP" "$RSS" "$HEAP" "$CPU" "$THREADS"
    
    sleep 1
done
