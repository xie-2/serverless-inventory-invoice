# PowerShell script to install dependencies for the Inventory System
# Run this script from the project root directory

Write-Host "Installing dependencies for Inventory System..." -ForegroundColor Cyan
Write-Host ""

# Install Node.js dependencies
Write-Host "📦 Installing Node.js dependencies..." -ForegroundColor Yellow
Set-Location node-service
if (Test-Path "package.json") {
    npm install
    Write-Host "✅ Node.js dependencies installed successfully" -ForegroundColor Green
} else {
    Write-Host "❌ package.json not found in node-service directory" -ForegroundColor Red
    exit 1
}
Set-Location ..

# Install Java/Maven dependencies (if Maven is available)
Write-Host ""
Write-Host "📦 Installing Java/Maven dependencies..." -ForegroundColor Yellow
Set-Location java-service
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    mvn dependency:resolve
    Write-Host "✅ Java dependencies resolved successfully" -ForegroundColor Green
} else {
    Write-Host "⚠️  Maven not found. Java dependencies will be downloaded during Docker build." -ForegroundColor Yellow
    Write-Host "   To install Maven:" -ForegroundColor Yellow
    Write-Host "   - Download from: https://maven.apache.org/download.cgi" -ForegroundColor Yellow
    Write-Host "   - Or use Chocolatey: choco install maven" -ForegroundColor Yellow
}
Set-Location ..

Write-Host ""
Write-Host "✅ Dependency installation complete!" -ForegroundColor Green
Write-Host ""
Write-Host "To start the system, run:" -ForegroundColor Cyan
Write-Host "  docker-compose up --build" -ForegroundColor White
