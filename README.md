# Serverless E-Commerce Inventory & Invoicing System

A cloud-native backend system for managing products, inventory, customer orders, and automatic invoice generation.

## Architecture

The system consists of two services:

1. **Java Spring Boot Core Service** - Handles all business logic, database writes, and order processing
2. **Node.js Orchestrator Service** - Handles API gateway, PDF generation, S3 uploads, and email delivery

```
Client
  │
  ▼
Node.js API Gateway (Express)
  │
  ▼
Java Spring Boot Core Service
  │
  ▼
PostgreSQL (AWS RDS)

Java ──► Node (order completed callback)
Node ──► S3 (PDF invoices)
Node ──► SES (email)
```

## Technology Stack

- **Core Backend**: Java 17 + Spring Boot 3.2.0
- **Orchestration**: Node.js 18 + Express
- **Database**: PostgreSQL 15
- **Storage**: AWS S3
- **Email**: AWS SES
- **Containers**: Docker

## Prerequisites

- Docker and Docker Compose
- Java 17+ (for local development)
- Node.js 18+ (for local development)
- Maven 3.9+ (for local development)
- AWS Account with S3 and SES configured (for production)

## Quick Start

### Using Docker Compose

1. Clone the repository:
```bash
git clone <repository-url>
cd Inventory
```

2. Set environment variables (optional, for AWS):
```bash
export S3_BUCKET=your-invoice-bucket
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export FROM_EMAIL=your-verified-ses-email
```

3. Start all services:
```bash
docker-compose up --build
```

This will start:
- PostgreSQL on port 5432
- Java service on port 8080
- Node.js service on port 3000

### Local Development

#### Java Service

```bash
cd java-service
mvn clean install
mvn spring-boot:run
```

The service will run on `http://localhost:8080`

#### Node.js Service

```bash
cd node-service
npm install
npm start
```

The service will run on `http://localhost:3000`

#### Database Setup

Ensure PostgreSQL is running and create the database:

```bash
createdb inventory_db
```

Flyway will automatically run migrations on startup.

## API Endpoints

### Create Order

**Endpoint**: `POST /api/orders`

**Request**:
```json
{
  "customerId": 1,
  "items": [
    { "productId": 1, "quantity": 2 }
  ]
}
```

**Response**:
```json
{
  "id": 1,
  "orderId": "ORD-2026-000001",
  "customerId": 1,
  "customerName": "John Doe",
  "customerEmail": "john@example.com",
  "items": [
    {
      "productId": 1,
      "productName": "Product Name",
      "quantity": 2,
      "unitPrice": 19.99,
      "totalPrice": 39.98
    }
  ],
  "subtotal": 39.98,
  "tax": 4.00,
  "total": 43.98,
  "status": "COMPLETED",
  "createdAt": "2026-01-15T10:30:00"
}
```

### Get Order

**Endpoint**: `GET /orders/{orderId}`

**Response**: Same as create order response

## Database Schema

### Tables

- **customers**: Customer information
- **products**: Product catalog with inventory
- **orders**: Order headers
- **order_items**: Order line items

### Key Constraints

- All monetary values stored as cents (BIGINT) - no floating-point
- Inventory quantity cannot go negative
- Foreign keys enforce referential integrity
- Row-level locking prevents inventory race conditions

## Invoice Generation Flow

1. Client creates order via Node.js API gateway
2. Node.js forwards request to Java service
3. Java service validates inventory, processes order transactionally
4. After transaction commit, Java calls Node.js callback endpoint
5. Node.js generates PDF invoice
6. Node.js uploads PDF to S3
7. Node.js sends email via AWS SES

## Configuration

### Java Service

Environment variables:
- `DATABASE_URL`: PostgreSQL connection string
- `DATABASE_USERNAME`: Database username
- `DATABASE_PASSWORD`: Database password
- `SERVER_PORT`: Server port (default: 8080)
- `ORCHESTRATOR_CALLBACK_URL`: Node.js callback URL

### Node.js Service

Environment variables:
- `PORT`: Server port (default: 3000)
- `JAVA_SERVICE_URL`: Java service URL
- `S3_BUCKET`: S3 bucket for invoices
- `AWS_REGION`: AWS region
- `AWS_ACCESS_KEY_ID`: AWS access key
- `AWS_SECRET_ACCESS_KEY`: AWS secret key
- `BUSINESS_NAME`: Business name for invoices
- `FROM_EMAIL`: Email address for sending invoices

## AWS Setup

### S3 Bucket

1. Create an S3 bucket for storing invoices
2. Configure bucket permissions
3. Set `S3_BUCKET` environment variable

### SES Email

1. Verify your sending email address in SES
2. If in sandbox mode, verify recipient emails too
3. Set `FROM_EMAIL` environment variable
4. Configure IAM permissions for SES

## Development Notes

### Transaction Safety

- Order creation is atomic - all or nothing
- Inventory updates use row-level locking (`SELECT ... FOR UPDATE`)
- Callbacks fire only after transaction commit
- Idempotent invoice generation allows safe retries

### Error Handling

- Explicit error responses (no silent failures)
- Meaningful exception messages
- Defensive programming throughout
- Proper logging at all levels

### Testing

Run tests:
```bash
# Java service
cd java-service
mvn test

# Node.js service
cd node-service
npm test
```

## Deployment

### AWS ECS Fargate

1. Build Docker images
2. Push to ECR
3. Create ECS task definitions
4. Deploy services to Fargate
5. Configure RDS PostgreSQL instance
6. Set environment variables in ECS

### AWS Elastic Beanstalk

1. Package applications
2. Deploy via EB CLI or console
3. Configure RDS
4. Set environment variables


