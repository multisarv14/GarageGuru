import React, { useEffect, useState } from 'react';
import axios from 'axios';

const formStyle = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
  gap: '12px',
  marginBottom: '20px',
};

const inputStyle = {
  padding: '10px',
  borderRadius: '8px',
  border: '1px solid #d1d5db',
};

const listStyle = {
  listStyle: 'none',
  padding: 0,
};

const listItemStyle = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  border: '1px solid #e5e7eb',
  borderRadius: '8px',
  padding: '12px',
  marginBottom: '10px',
};

function Customers({ apiBaseUrl }) {
  const [customers, setCustomers] = useState([]);
  const [formData, setFormData] = useState({ name: '', email: '', phone: '' });

  const loadCustomers = async () => {
    try {
      const response = await axios.get(`${apiBaseUrl}/customers`);
      setCustomers(response.data);
    } catch (error) {
      console.error('Failed to load customers:', error);
    }
  };

  useEffect(() => {
    loadCustomers();
  }, []);

  const handleChange = (event) => {
    const { name, value } = event.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    try {
      await axios.post(`${apiBaseUrl}/customers`, formData);
      setFormData({ name: '', email: '', phone: '' });
      loadCustomers();
    } catch (error) {
      console.error('Failed to add customer:', error);
    }
  };

  const handleDelete = async (id) => {
    try {
      await axios.delete(`${apiBaseUrl}/customers/${id}`);
      loadCustomers();
    } catch (error) {
      console.error('Failed to delete customer:', error);
    }
  };

  return (
    <section>
      <h2>Customers</h2>
      <form onSubmit={handleSubmit} style={formStyle}>
        <input name="name" placeholder="Name" value={formData.name} onChange={handleChange} style={inputStyle} />
        <input name="email" placeholder="Email" value={formData.email} onChange={handleChange} style={inputStyle} />
        <input name="phone" placeholder="Phone" value={formData.phone} onChange={handleChange} style={inputStyle} />
        <button type="submit">Add Customer</button>
      </form>

      <ul style={listStyle}>
        {customers.map((customer) => (
          <li key={customer.id} style={listItemStyle}>
            <div>
              <strong>{customer.name}</strong>
              <div>{customer.email}</div>
              <div>{customer.phone}</div>
            </div>
            <button type="button" onClick={() => handleDelete(customer.id)}>
              Delete
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default Customers;
