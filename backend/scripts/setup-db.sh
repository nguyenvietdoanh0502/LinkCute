#!/bin/bash

# Configuration
DB_NAME="roamly"
DB_USER="postgres"

echo "Checking if database '$DB_NAME' exists..."

# Check if database exists using psql
DB_EXISTS=$(psql -U $DB_USER -lqt | cut -d \| -f 1 | grep -w $DB_NAME | wc -l)

if [ $DB_EXISTS -eq 0 ]; then
    echo "Database '$DB_NAME' does not exist. Creating..."
    psql -U $DB_USER -c "CREATE DATABASE $DB_NAME"
    if [ $? -eq 0 ]; then
        echo "Database '$DB_NAME' created successfully."
    else
        echo "Error: Failed to create database '$DB_NAME'."
        exit 1
    fi
else
    echo "Database '$DB_NAME' already exists. Skipping creation."
fi
