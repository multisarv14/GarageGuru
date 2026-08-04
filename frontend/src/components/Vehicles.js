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

function Vehicles({ apiBaseUrl }) {
  const [vehicles, setVehicles] = useState([]);
  const [formData, setFormData] = useState({
    make: '',
    model: '',
    year: '',
    licensePlate: '',
    ownerId: '',
  });

  const loadVehicles = async () => {
    try {
      const response = await axios.get(`${apiBaseUrl}/vehicles`);
      setVehicles(response.data);
    } catch (error) {
      console.error('Failed to load vehicles:', error);
    }
  };

  useEffect(() => {
    loadVehicles();
  }, []);

  const handleChange = (event) => {
    const { name, value } = event.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    try {
      await axios.post(`${apiBaseUrl}/vehicles`, {
        ...formData,
        year: Number(formData.year),
      });
      setFormData({ make: '', model: '', year: '', licensePlate: '', ownerId: '' });
      loadVehicles();
    } catch (error) {
      console.error('Failed to add vehicle:', error);
    }
  };

  const handleDelete = async (id) => {
    try {
      await axios.delete(`${apiBaseUrl}/vehicles/${id}`);
      loadVehicles();
    } catch (error) {
      console.error('Failed to delete vehicle:', error);
    }
  };

  return (
    <section>
      <h2>Vehicles</h2>
      <form onSubmit={handleSubmit} style={formStyle}>
        <input name="make" placeholder="Make" value={formData.make} onChange={handleChange} style={inputStyle} />
        <input name="model" placeholder="Model" value={formData.model} onChange={handleChange} style={inputStyle} />
        <input name="year" placeholder="Year" value={formData.year} onChange={handleChange} style={inputStyle} />
        <input
          name="licensePlate"
          placeholder="License Plate"
          value={formData.licensePlate}
          onChange={handleChange}
          style={inputStyle}
        />
        <input name="ownerId" placeholder="Owner ID" value={formData.ownerId} onChange={handleChange} style={inputStyle} />
        <button type="submit">Add Vehicle</button>
      </form>

      <ul style={listStyle}>
        {vehicles.map((vehicle) => (
          <li key={vehicle.id} style={listItemStyle}>
            <div>
              <strong>
                {vehicle.make} {vehicle.model}
              </strong>
              <div>Year: {vehicle.year}</div>
              <div>Plate: {vehicle.licensePlate}</div>
              <div>Owner ID: {vehicle.ownerId}</div>
            </div>
            <button type="button" onClick={() => handleDelete(vehicle.id)}>
              Delete
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default Vehicles;
