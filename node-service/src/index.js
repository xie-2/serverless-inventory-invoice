const express = require('express');
const axios = require('axios');
const { handleOrderCompleted } = require('./handlers/orderCompletedHandler');

const app = express();
const PORT = process.env.PORT || 3000;
const JAVA_SERVICE_URL = process.env.JAVA_SERVICE_URL || 'http://localhost:8080';

app.use(express.json());

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({ status: 'ok', service: 'orchestrator' });
});

// Forward order creation requests to Java service
app.post('/api/orders', async (req, res) => {
    try {
        console.log('Forwarding order creation request to Java service');
        const response = await axios.post(`${JAVA_SERVICE_URL}/orders`, req.body, {
            headers: {
                'Content-Type': 'application/json'
            }
        });
        res.status(response.status).json(response.data);
    } catch (error) {
        console.error('Error forwarding request to Java service:', error.message);
        if (error.response) {
            res.status(error.response.status).json({
                error: error.response.data || error.message
            });
        } else {
            res.status(500).json({
                error: 'Internal server error while forwarding request'
            });
        }
    }
});

// Internal callback endpoint for order completion
app.post('/internal/order-completed', async (req, res) => {
    try {
        const { orderId } = req.body;
        
        if (!orderId) {
            return res.status(400).json({ error: 'orderId is required' });
        }

        console.log(`Received order completion callback for orderId: ${orderId}`);

        // Handle order completion asynchronously (don't block the callback)
        handleOrderCompleted(orderId)
            .then(() => {
                console.log(`Successfully processed order completion for orderId: ${orderId}`);
            })
            .catch((error) => {
                console.error(`Error processing order completion for orderId: ${orderId}`, error);
                // Log error but don't fail the callback - idempotency allows retries
            });

        // Return success immediately (idempotent operation)
        res.status(200).json({ 
            status: 'accepted',
            message: `Order completion callback received for orderId: ${orderId}`
        });
    } catch (error) {
        console.error('Error handling order completion callback:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

app.listen(PORT, () => {
    console.log(`Orchestrator service listening on port ${PORT}`);
    console.log(`Java service URL: ${JAVA_SERVICE_URL}`);
});
