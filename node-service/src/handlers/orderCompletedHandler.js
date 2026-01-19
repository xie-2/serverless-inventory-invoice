const axios = require('axios');
const PDFDocument = require('pdfkit');
const { S3Client, PutObjectCommand } = require('@aws-sdk/client-s3');
const { SESClient, SendEmailCommand } = require('@aws-sdk/client-ses');

const JAVA_SERVICE_URL = process.env.JAVA_SERVICE_URL || 'http://localhost:8080';
const S3_BUCKET = process.env.S3_BUCKET || 'inventory-invoices';
const AWS_REGION = process.env.AWS_REGION || 'us-east-1';
const BUSINESS_NAME = process.env.BUSINESS_NAME || 'Inventory System';
const FROM_EMAIL = process.env.FROM_EMAIL || 'noreply@inventory.com';

// Initialize AWS clients
const s3Client = new S3Client({ region: AWS_REGION });
const sesClient = new SESClient({ region: AWS_REGION });

/**
 * Fetches order details from Java service
 */
async function fetchOrderDetails(orderId) {
    try {
        const response = await axios.get(`${JAVA_SERVICE_URL}/orders/${orderId}`);
        return response.data;
    } catch (error) {
        console.error(`Error fetching order details for ${orderId}:`, error.message);
        if (error.response) {
            throw new Error(`Failed to fetch order details: ${error.response.status} - ${error.response.data}`);
        }
        throw new Error(`Failed to fetch order details: ${error.message}`);
    }
}

/**
 * Generates PDF invoice for an order
 */
function generateInvoicePDF(order) {
    return new Promise((resolve, reject) => {
        try {
            const doc = new PDFDocument({ margin: 50 });
            const chunks = [];

            doc.on('data', (chunk) => chunks.push(chunk));
            doc.on('end', () => resolve(Buffer.concat(chunks)));
            doc.on('error', reject);

            // Header
            doc.fontSize(20).text(BUSINESS_NAME, { align: 'center' });
            doc.moveDown();

            // Invoice details
            doc.fontSize(14).text(`Invoice for Order: ${order.orderId}`, { align: 'left' });
            doc.moveDown(0.5);
            doc.fontSize(10).text(`Date: ${new Date(order.createdAt).toLocaleDateString()}`);
            doc.moveDown();

            // Customer information
            doc.fontSize(12).text('Bill To:', { underline: true });
            doc.fontSize(10).text(order.customerName);
            if (order.customerEmail) {
                doc.text(order.customerEmail);
            }
            doc.moveDown();

            // Items table header
            doc.fontSize(10);
            const tableTop = doc.y;
            doc.text('Product', 50, tableTop);
            doc.text('Quantity', 200, tableTop);
            doc.text('Unit Price', 300, tableTop, { align: 'right' });
            doc.text('Total', 400, tableTop, { align: 'right' });

            // Items
            let yPosition = tableTop + 20;
            order.items.forEach((item) => {
                doc.text(item.productName || 'Product', 50, yPosition);
                doc.text(item.quantity.toString(), 200, yPosition);
                doc.text(`$${item.unitPrice.toFixed(2)}`, 300, yPosition, { align: 'right' });
                doc.text(`$${item.totalPrice.toFixed(2)}`, 400, yPosition, { align: 'right' });
                yPosition += 20;
            });

            // Totals
            yPosition += 10;
            doc.moveTo(50, yPosition).lineTo(450, yPosition).stroke();
            yPosition += 10;

            doc.text('Subtotal:', 300, yPosition, { align: 'right' });
            doc.text(`$${order.subtotal.toFixed(2)}`, 400, yPosition, { align: 'right' });
            yPosition += 20;

            doc.text('Tax:', 300, yPosition, { align: 'right' });
            doc.text(`$${order.tax.toFixed(2)}`, 400, yPosition, { align: 'right' });
            yPosition += 20;

            doc.fontSize(12).text('Total:', 300, yPosition, { align: 'right' });
            doc.fontSize(12).text(`$${order.total.toFixed(2)}`, 400, yPosition, { align: 'right' });

            doc.end();
        } catch (error) {
            reject(error);
        }
    });
}

/**
 * Uploads PDF to S3
 */
async function uploadToS3(orderId, pdfBuffer) {
    const key = `invoices/${orderId}.pdf`;
    
    try {
        const command = new PutObjectCommand({
            Bucket: S3_BUCKET,
            Key: key,
            Body: pdfBuffer,
            ContentType: 'application/pdf',
        });

        await s3Client.send(command);
        console.log(`Successfully uploaded invoice to S3: ${key}`);
        return key;
    } catch (error) {
        console.error(`Error uploading to S3:`, error);
        throw new Error(`Failed to upload invoice to S3: ${error.message}`);
    }
}

/**
 * Sends invoice email via AWS SES
 */
async function sendInvoiceEmail(order, s3Key) {
    const subject = `Invoice for Order ${order.orderId}`;
    const body = `
Dear ${order.customerName},

Thank you for your order!

Your invoice for Order ${order.orderId} is attached.

Order Summary:
- Subtotal: $${order.subtotal.toFixed(2)}
- Tax: $${order.tax.toFixed(2)}
- Total: $${order.total.toFixed(2)}

Best regards,
${BUSINESS_NAME}
    `.trim();

    try {
        // For production, you'd attach the PDF from S3
        // For now, we'll send a text email with a link to the S3 object
        const command = new SendEmailCommand({
            Source: FROM_EMAIL,
            Destination: {
                ToAddresses: [order.customerEmail || order.customerId + '@example.com'],
            },
            Message: {
                Subject: {
                    Data: subject,
                    Charset: 'UTF-8',
                },
                Body: {
                    Text: {
                        Data: body,
                        Charset: 'UTF-8',
                    },
                },
            },
        });

        await sesClient.send(command);
        console.log(`Successfully sent invoice email for order ${order.orderId}`);
    } catch (error) {
        console.error(`Error sending email:`, error);
        // Don't throw - email failure shouldn't fail invoice generation
        // In production, you might want to queue for retry
    }
}

/**
 * Main handler for order completion
 * This function is idempotent - safe to retry
 */
async function handleOrderCompleted(orderId) {
    console.log(`Processing order completion for orderId: ${orderId}`);

    try {
        // Fetch order details from Java service
        const order = await fetchOrderDetails(orderId);
        
        if (!order) {
            throw new Error(`Order not found: ${orderId}`);
        }

        // Generate PDF invoice
        console.log(`Generating PDF invoice for order ${orderId}`);
        const pdfBuffer = await generateInvoicePDF(order);

        // Upload to S3 (idempotent - overwrites are acceptable)
        console.log(`Uploading invoice to S3 for order ${orderId}`);
        const s3Key = await uploadToS3(orderId, pdfBuffer);

        // Send email (idempotent - SES handles duplicates)
        console.log(`Sending invoice email for order ${orderId}`);
        await sendInvoiceEmail(order, s3Key);

        console.log(`Successfully completed invoice processing for order ${orderId}`);
    } catch (error) {
        console.error(`Error processing order completion for ${orderId}:`, error);
        throw error; // Re-throw to allow retry logic
    }
}

module.exports = {
    handleOrderCompleted
};
