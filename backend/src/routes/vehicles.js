const express = require('express');
const { v4: uuidv4 } = require('uuid');
const db = require('../db');

const router = express.Router();

router.get('/', (req, res) => {
  res.json(db.vehicles);
});

router.get('/:id', (req, res) => {
  const vehicle = db.vehicles.find((item) => item.id === req.params.id);

  if (!vehicle) {
    return res.status(404).json({ message: 'Vehicle not found.' });
  }

  return res.json(vehicle);
});

router.post('/', (req, res) => {
  const { make, model, year, licensePlate, ownerId } = req.body;

  if (!make || !model || !year || !licensePlate || !ownerId) {
    return res.status(400).json({ message: 'Make, model, year, license plate, and owner ID are required.' });
  }

  const vehicle = {
    id: uuidv4(),
    make,
    model,
    year,
    licensePlate,
    ownerId,
  };

  db.vehicles.push(vehicle);
  return res.status(201).json(vehicle);
});

router.put('/:id', (req, res) => {
  const vehicle = db.vehicles.find((item) => item.id === req.params.id);

  if (!vehicle) {
    return res.status(404).json({ message: 'Vehicle not found.' });
  }

  const { make, model, year, licensePlate, ownerId } = req.body;

  vehicle.make = make ?? vehicle.make;
  vehicle.model = model ?? vehicle.model;
  vehicle.year = year ?? vehicle.year;
  vehicle.licensePlate = licensePlate ?? vehicle.licensePlate;
  vehicle.ownerId = ownerId ?? vehicle.ownerId;

  return res.json(vehicle);
});

router.delete('/:id', (req, res) => {
  const index = db.vehicles.findIndex((item) => item.id === req.params.id);

  if (index === -1) {
    return res.status(404).json({ message: 'Vehicle not found.' });
  }

  const [deletedVehicle] = db.vehicles.splice(index, 1);
  return res.json(deletedVehicle);
});

module.exports = router;
