#!/bin/bash

# MinIO Docker stop script

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Stopping MinIO server...${NC}"

# Stop and remove the MinIO container
docker stop minio-server 2>/dev/null
docker rm minio-server 2>/dev/null

if [ $? -eq 0 ]; then
    echo -e "${GREEN}MinIO server stopped successfully!${NC}"
else
    echo -e "${RED}MinIO server was not running or failed to stop${NC}"
fi