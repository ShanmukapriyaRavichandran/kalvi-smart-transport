const express = require('express');
const path = require('path');
const apiApp = require('./api/index');

const app = express();
const PORT = process.env.PORT || 8080;

// Mount API
app.use(apiApp);

// Serve static frontend files
const staticPath = path.join(__dirname, 'public');
app.use(express.static(staticPath));

// Fallback to index.html for SPA
app.get('*', (req, res) => {
    res.sendFile(path.join(staticPath, 'index.html'));
});

app.listen(PORT, () => {
    console.log(`========================================================================`);
    console.log(`  KALVI COLLEGE SMART TRANSPORT MANAGEMENT SYSTEM (NODE/EXPRESS SERVER) `);
    console.log(`========================================================================`);
    console.log(`  - Local URL:   http://localhost:${PORT}`);
    console.log(`  - REST API:    http://localhost:${PORT}/api/routes/optimize?bus_id=B01`);
    console.log(`========================================================================`);
});
