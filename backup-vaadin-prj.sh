#!/bin/bash

# Backup script for vaadin-prj (excluding minio/data directory)

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_NAME="vaadin-prj-backup-${TIMESTAMP}.tar.gz"
SOURCE_DIR="/home/valerio/prj/vaadin-prj"
BACKUP_DIR="/home/valerio/prj"

echo "📦 Creating backup of vaadin-prj..."
echo "Backup file: ${BACKUP_DIR}/${BACKUP_NAME}"
echo "Excluding: minio/data directory"

cd /home/valerio/prj

tar -czf "${BACKUP_DIR}/${BACKUP_NAME}" \
    --exclude='vaadin-prj/minio/data' \
    --exclude='vaadin-prj/*/target' \
    --exclude='vaadin-prj/*/node_modules' \
    --exclude='vaadin-prj/*/.git' \
    vaadin-prj/

if [ $? -eq 0 ]; then
    echo "✅ Backup created successfully!"
    echo "📍 Location: ${BACKUP_DIR}/${BACKUP_NAME}"
    ls -lh "${BACKUP_DIR}/${BACKUP_NAME}"
else
    echo "❌ Backup failed!"
    exit 1
fi
