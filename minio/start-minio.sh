#!/bin/bash

# MinIO Docker startup script
# This script starts a MinIO server using Docker

# Configuration
MINIO_ROOT_USER=${MINIO_ROOT_USER:-minioadmin}
MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD:-minioadmin123}
MINIO_PORT=${MINIO_PORT:-9000}
MINIO_CONSOLE_PORT=${MINIO_CONSOLE_PORT:-9001}
MINIO_DATA_DIR=${MINIO_DATA_DIR:-$(pwd)/data}

# Create data directory if it doesn't exist
mkdir -p "$MINIO_DATA_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting MinIO server...${NC}"
echo -e "${YELLOW}Configuration:${NC}"
echo -e "  - Root User: ${MINIO_ROOT_USER}"
echo -e "  - Root Password: ${MINIO_ROOT_PASSWORD}"
echo -e "  - API Port: ${MINIO_PORT}"
echo -e "  - Console Port: ${MINIO_CONSOLE_PORT}"
echo -e "  - Data Directory: ${MINIO_DATA_DIR}"

# Check if MinIO container is already running
if docker ps | grep -q "minio-server"; then
    echo -e "${YELLOW}MinIO container is already running. Stopping it first...${NC}"
    docker stop minio-server
    docker rm minio-server
fi

# Run MinIO container
docker run -d \
    --name minio-server \
    --restart unless-stopped \
    -p ${MINIO_PORT}:9000 \
    -p ${MINIO_CONSOLE_PORT}:9001 \
    -v "${MINIO_DATA_DIR}:/data" \
    -e MINIO_ROOT_USER="${MINIO_ROOT_USER}" \
    -e MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD}" \
    minio/minio server /data --console-address ":9001"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}MinIO server started successfully!${NC}"
    echo -e "${GREEN}Access URLs:${NC}"
    echo -e "  - API: http://localhost:${MINIO_PORT}"
    echo -e "  - Console: http://localhost:${MINIO_CONSOLE_PORT}"
    echo -e "${YELLOW}Login credentials:${NC}"
    echo -e "  - Username: ${MINIO_ROOT_USER}"
    echo -e "  - Password: ${MINIO_ROOT_PASSWORD}"
    echo ""
    echo -e "${YELLOW}To stop MinIO:${NC}"
    echo -e "  docker stop minio-server"
    echo ""
    echo -e "${YELLOW}To view logs:${NC}"
    echo -e "  docker logs -f minio-server"
else
    echo -e "${RED}Failed to start MinIO server${NC}"
    exit 1
fi