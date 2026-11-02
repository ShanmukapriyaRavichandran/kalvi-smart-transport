const express = require('express');
const path = require('path');
const apiApp = require('./api/index');

const app = express();
const PORT = process.env.PORT || 8080;

// Mount API
app.use(apiApp);

// Serve static frontend files from project root
app.use(express.static(__dirname));

// Fallback to index.html for SPA routing
app.get('*', (req, res) => {
    res.sendFile(path.join(__dirname, 'index.html'));
});

// Only listen when run directly via terminal/node, not when imported by serverless loaders
if (require.main === module) {
    app.listen(PORT, () => {
        console.log(`========================================================================`);
        console.log(`  KALVI COLLEGE SMART TRANSPORT MANAGEMENT SYSTEM (NODE/EXPRESS SERVER) `);
        console.log(`========================================================================`);
        console.log(`  - Local URL:   http://localhost:${PORT}`);
        console.log(`  - REST API:    http://localhost:${PORT}/api/routes/optimize?bus_id=B01`);
        console.log(`========================================================================`);
    });
}

module.exports = app;
