import React, { useEffect, useState } from 'react';
import axios from 'axios';

const formStyle = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
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

function Services({ apiBaseUrl }) {
  const [services, setServices] = useState([]);
  const [formData, setFormData] = useState({
    vehicleId: '',
    description: '',
    date: '',
    status: '',
    cost: '',
  });

  const loadServices = async () => {
    try {
      const response = await axios.get(`${apiBaseUrl}/services`);
      setServices(response.data);
    } catch (error) {
      console.error('Failed to load services:', error);
    }
  };

  useEffect(() => {
    loadServices();
  }, []);

  const handleChange = (event) => {
    const { name, value } = event.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    try {
      await axios.post(`${apiBaseUrl}/services`, {
        ...formData,
        cost: Number(formData.cost),
      });
      setFormData({ vehicleId: '', description: '', date: '', status: '', cost: '' });
      loadServices();
    } catch (error) {
      console.error('Failed to add service record:', error);
    }
  };

  const handleDelete = async (id) => {
    try {
      await axios.delete(`${apiBaseUrl}/services/${id}`);
      loadServices();
    } catch (error) {
      console.error('Failed to delete service record:', error);
    }
  };

  return (
    <section>
      <h2>Service Records</h2>
      <form onSubmit={handleSubmit} style={formStyle}>
        <input name="vehicleId" placeholder="Vehicle ID" value={formData.vehicleId} onChange={handleChange} style={inputStyle} />
        <input
          name="description"
          placeholder="Description"
          value={formData.description}
          onChange={handleChange}
          style={inputStyle}
        />
        <input name="date" placeholder="Date" value={formData.date} onChange={handleChange} style={inputStyle} />
        <input name="status" placeholder="Status" value={formData.status} onChange={handleChange} style={inputStyle} />
        <input name="cost" placeholder="Cost" value={formData.cost} onChange={handleChange} style={inputStyle} />
        <button type="submit">Add Service</button>
      </form>

      <ul style={listStyle}>
        {services.map((service) => (
          <li key={service.id} style={listItemStyle}>
            <div>
              <strong>{service.description}</strong>
              <div>Vehicle ID: {service.vehicleId}</div>
              <div>Date: {service.date}</div>
              <div>Status: {service.status}</div>
              <div>Cost: ${service.cost}</div>
            </div>
            <button type="button" onClick={() => handleDelete(service.id)}>
              Delete
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default Services;
