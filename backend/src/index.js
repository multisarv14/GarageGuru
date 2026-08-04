const express = require('express');
const cors = require('cors');
const customerRoutes = require('./routes/customers');
const vehicleRoutes = require('./routes/vehicles');
const serviceRoutes = require('./routes/services');
const db = require('./db');

const app = express();
const port = process.env.PORT || 5000;

app.use(cors());
app.use(express.json());

app.get('/', (req, res) => {
  res.json({
    message: 'Welcome to GarageGuruApp API',
    endpoints: ['/api/customers', '/api/vehicles', '/api/services', '/api/dashboard'],
  });
});

app.get('/api/dashboard', (req, res) => {
  res.json({
    customers: db.customers.length,
    vehicles: db.vehicles.length,
    services: db.services.length,
  });
});

app.use('/api/customers', customerRoutes);
app.use('/api/vehicles', vehicleRoutes);
app.use('/api/services', serviceRoutes);

app.listen(port, () => {
  console.log(`GarageGuru backend running on port ${port}`);
});
