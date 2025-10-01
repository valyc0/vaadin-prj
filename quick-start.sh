#!/bin/bash

# Quick Start Script for Vaadin Microservice Project
# Usage: ./quick-start.sh [build|run|stop|clean]

set -e

PROJECT_ROOT="/workspace/db-ready/vaadin-prj"
KEYCLOAK_DIR="$PROJECT_ROOT/docker"
ROLES_SERVICE_DIR="$PROJECT_ROOT/roles-service"
VAADIN_APP_DIR="$PROJECT_ROOT/vaadin-app"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}╔════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║   Vaadin Microservice - Modular Architecture  ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════╝${NC}"
}

print_step() {
    echo -e "${GREEN}▶${NC} $1"
}

print_error() {
    echo -e "${RED}✖${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

build_project() {
    print_step "Building project..."
    cd "$PROJECT_ROOT"
    
    print_step "Building common-dto..."
    cd "$PROJECT_ROOT/common-dto"
    mvn clean install -DskipTests
    
    print_step "Building entity module..."
    cd "$PROJECT_ROOT/entity"
    mvn clean install -DskipTests
    
    print_step "Building roles-service..."
    cd "$PROJECT_ROOT/roles-service"
    mvn clean install -DskipTests
    
    print_step "Building vaadin-app..."
    cd "$PROJECT_ROOT/vaadin-app"
    mvn clean install -Pproduction -DskipTests
    
    print_success "Build completed successfully!"
}

start_keycloak() {
    print_step "Starting Keycloak..."
    cd "$KEYCLOAK_DIR"
    
    if docker-compose ps | grep -q "keycloak.*Up"; then
        print_warning "Keycloak is already running"
    else
        docker-compose up -d
        print_success "Keycloak started on http://localhost:8081"
        print_step "Waiting for Keycloak to be ready..."
        sleep 10
    fi
}

start_roles_service() {
    print_step "Starting Roles Service..."
    cd "$ROLES_SERVICE_DIR"
    
    if [ -f "roles-service.pid" ]; then
        PID=$(cat roles-service.pid)
        if ps -p $PID > /dev/null 2>&1; then
            print_warning "Roles Service is already running (PID: $PID)"
            return
        fi
    fi
    
    mvn spring-boot:run > roles-service.log 2>&1 &
    echo $! > roles-service.pid
    print_success "Roles Service started on http://localhost:8091"
    print_step "  - API: http://localhost:8091/api/roles"
    print_step "  - Swagger: http://localhost:8091/swagger-ui.html"
    print_step "  - H2 Console: http://localhost:8091/h2-console"
}

start_vaadin_app() {
    print_step "Starting Vaadin App..."
    cd "$VAADIN_APP_DIR"
    
    if [ -f "vaadin-app.pid" ]; then
        PID=$(cat vaadin-app.pid)
        if ps -p $PID > /dev/null 2>&1; then
            print_warning "Vaadin App is already running (PID: $PID)"
            return
        fi
    fi
    
    mvn spring-boot:run > vaadin-app.log 2>&1 &
    echo $! > vaadin-app.pid
    print_success "Vaadin App started on http://localhost:8080"
}

stop_services() {
    print_step "Stopping services..."
    
    # Stop Vaadin App
    if [ -f "$VAADIN_APP_DIR/vaadin-app.pid" ]; then
        PID=$(cat "$VAADIN_APP_DIR/vaadin-app.pid")
        if ps -p $PID > /dev/null 2>&1; then
            kill $PID
            rm "$VAADIN_APP_DIR/vaadin-app.pid"
            print_success "Vaadin App stopped"
        fi
    fi
    
    # Stop Roles Service
    if [ -f "$ROLES_SERVICE_DIR/roles-service.pid" ]; then
        PID=$(cat "$ROLES_SERVICE_DIR/roles-service.pid")
        if ps -p $PID > /dev/null 2>&1; then
            kill $PID
            rm "$ROLES_SERVICE_DIR/roles-service.pid"
            print_success "Roles Service stopped"
        fi
    fi
    
    # Stop Keycloak
    cd "$KEYCLOAK_DIR"
    if docker-compose ps | grep -q "keycloak.*Up"; then
        docker-compose down
        print_success "Keycloak stopped"
    fi
}

clean_project() {
    print_step "Cleaning project..."
    cd "$PROJECT_ROOT"
    mvn clean
    
    # Remove log files
    rm -f "$ROLES_SERVICE_DIR/roles-service.log"
    rm -f "$VAADIN_APP_DIR/vaadin-app.log"
    
    print_success "Project cleaned"
}

show_status() {
    print_header
    echo ""
    echo "Service Status:"
    echo ""
    
    # Keycloak
    cd "$KEYCLOAK_DIR"
    if docker-compose ps | grep -q "keycloak.*Up"; then
        echo -e "  ${GREEN}●${NC} Keycloak:      http://localhost:8081 ${GREEN}(running)${NC}"
    else
        echo -e "  ${RED}●${NC} Keycloak:      http://localhost:8081 ${RED}(stopped)${NC}"
    fi
    
    # Roles Service
    if [ -f "$ROLES_SERVICE_DIR/roles-service.pid" ]; then
        PID=$(cat "$ROLES_SERVICE_DIR/roles-service.pid")
        if ps -p $PID > /dev/null 2>&1; then
            echo -e "  ${GREEN}●${NC} Roles Service: http://localhost:8091 ${GREEN}(running)${NC}"
        else
            echo -e "  ${RED}●${NC} Roles Service: http://localhost:8091 ${RED}(stopped)${NC}"
        fi
    else
        echo -e "  ${RED}●${NC} Roles Service: http://localhost:8091 ${RED}(stopped)${NC}"
    fi
    
    # Vaadin App
    if [ -f "$VAADIN_APP_DIR/vaadin-app.pid" ]; then
        PID=$(cat "$VAADIN_APP_DIR/vaadin-app.pid")
        if ps -p $PID > /dev/null 2>&1; then
            echo -e "  ${GREEN}●${NC} Vaadin App:    http://localhost:8080 ${GREEN}(running)${NC}"
        else
            echo -e "  ${RED}●${NC} Vaadin App:    http://localhost:8080 ${RED}(stopped)${NC}"
        fi
    else
        echo -e "  ${RED}●${NC} Vaadin App:    http://localhost:8080 ${RED}(stopped)${NC}"
    fi
    
    echo ""
}

show_usage() {
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  build     - Build all modules"
    echo "  run       - Start all services (Keycloak, Roles Service, Vaadin App)"
    echo "  stop      - Stop all services"
    echo "  clean     - Clean build artifacts"
    echo "  status    - Show status of all services"
    echo "  restart   - Stop and start all services"
    echo ""
    echo "Examples:"
    echo "  $0 build      # Build the project"
    echo "  $0 run        # Start all services"
    echo "  $0 status     # Check service status"
    echo "  $0 stop       # Stop all services"
}

# Main
case "$1" in
    build)
        print_header
        build_project
        ;;
    run)
        print_header
        start_keycloak
        sleep 5
        start_roles_service
        sleep 5
        start_vaadin_app
        echo ""
        print_success "All services started successfully!"
        echo ""
        show_status
        ;;
    stop)
        print_header
        stop_services
        ;;
    clean)
        print_header
        clean_project
        ;;
    status)
        show_status
        ;;
    restart)
        print_header
        stop_services
        sleep 2
        start_keycloak
        sleep 5
        start_roles_service
        sleep 5
        start_vaadin_app
        echo ""
        print_success "All services restarted successfully!"
        ;;
    *)
        print_header
        echo ""
        show_usage
        exit 1
        ;;
esac
