const express = require('express');
const { v4: uuidv4 } = require('uuid');
const db = require('../db');

const router = express.Router();

router.get('/', (req, res) => {
  res.json(db.services);
});

router.get('/:id', (req, res) => {
  const service = db.services.find((item) => item.id === req.params.id);

  if (!service) {
    return res.status(404).json({ message: 'Service record not found.' });
  }

  return res.json(service);
});

router.post('/', (req, res) => {
  const { vehicleId, description, date, status, cost } = req.body;

  if (!vehicleId || !description || !date || !status || cost === undefined) {
    return res.status(400).json({ message: 'Vehicle ID, description, date, status, and cost are required.' });
  }

  const service = {
    id: uuidv4(),
    vehicleId,
    description,
    date,
    status,
    cost,
  };

  db.services.push(service);
  return res.status(201).json(service);
});

router.put('/:id', (req, res) => {
  const service = db.services.find((item) => item.id === req.params.id);

  if (!service) {
    return res.status(404).json({ message: 'Service record not found.' });
  }

  const { vehicleId, description, date, status, cost } = req.body;

  service.vehicleId = vehicleId ?? service.vehicleId;
  service.description = description ?? service.description;
  service.date = date ?? service.date;
  service.status = status ?? service.status;
  service.cost = cost ?? service.cost;

  return res.json(service);
});

router.delete('/:id', (req, res) => {
  const index = db.services.findIndex((item) => item.id === req.params.id);

  if (index === -1) {
    return res.status(404).json({ message: 'Service record not found.' });
  }

  const [deletedService] = db.services.splice(index, 1);
  return res.json(deletedService);
});

module.exports = router;
