const express = require('express');
const { v4: uuidv4 } = require('uuid');
const db = require('../db');

const router = express.Router();

router.get('/', (req, res) => {
  res.json(db.customers);
});

router.get('/:id', (req, res) => {
  const customer = db.customers.find((item) => item.id === req.params.id);

  if (!customer) {
    return res.status(404).json({ message: 'Customer not found.' });
  }

  return res.json(customer);
});

router.post('/', (req, res) => {
  const { name, email, phone } = req.body;

  if (!name || !email || !phone) {
    return res.status(400).json({ message: 'Name, email, and phone are required.' });
  }

  const customer = {
    id: uuidv4(),
    name,
    email,
    phone,
  };

  db.customers.push(customer);
  return res.status(201).json(customer);
});

router.put('/:id', (req, res) => {
  const customer = db.customers.find((item) => item.id === req.params.id);

  if (!customer) {
    return res.status(404).json({ message: 'Customer not found.' });
  }

  const { name, email, phone } = req.body;

  customer.name = name ?? customer.name;
  customer.email = email ?? customer.email;
  customer.phone = phone ?? customer.phone;

  return res.json(customer);
});

router.delete('/:id', (req, res) => {
  const index = db.customers.findIndex((item) => item.id === req.params.id);

  if (index === -1) {
    return res.status(404).json({ message: 'Customer not found.' });
  }

  const [deletedCustomer] = db.customers.splice(index, 1);
  return res.json(deletedCustomer);
});

module.exports = router;
