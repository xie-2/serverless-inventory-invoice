#!/bin/bash

# Script to install dependencies for the Inventory System
# Run this script from the project root directory

set -e

echo "Installing dependencies for Inventory System..."
echo ""

# Install Node.js dependencies
echo "📦 Installing Node.js dependencies..."
cd node-service
if [ -f "package.json" ]; then
    npm install
    echo "✅ Node.js dependencies installed successfully"
else
    echo "❌ package.json not found in node-service directory"
    exit 1
fi
cd ..

# Install Java/Maven dependencies (if Maven is available)
echo ""
echo "📦 Installing Java/Maven dependencies..."
cd java-service
if command -v mvn &> /dev/null; then
    mvn dependency:resolve
    echo "✅ Java dependencies resolved successfully"
else
    echo "⚠️  Maven not found. Java dependencies will be downloaded during Docker build."
    echo "   To install Maven:"
    echo "   - macOS: brew install maven"
    echo "   - Linux: sudo apt-get install maven (or use your package manager)"
    echo "   - Or download from: https://maven.apache.org/download.cgi"
fi
cd ..

echo ""
echo "✅ Dependency installation complete!"
echo ""
echo "To start the system, run:"
echo "  docker-compose up --build"
