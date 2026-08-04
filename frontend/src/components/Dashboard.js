import React, { useEffect, useState } from 'react';
import axios from 'axios';

const containerStyle = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
  gap: '16px',
};

const cardStyle = {
  border: '1px solid #d1d5db',
  borderRadius: '12px',
  padding: '20px',
  backgroundColor: '#f9fafb',
};

function Dashboard({ apiBaseUrl }) {
  const [counts, setCounts] = useState({ customers: 0, vehicles: 0, services: 0 });

  useEffect(() => {
    const loadCounts = async () => {
      try {
        const response = await axios.get(`${apiBaseUrl}/dashboard`);
        setCounts(response.data);
      } catch (error) {
        console.error('Failed to load dashboard data:', error);
      }
    };

    loadCounts();
  }, [apiBaseUrl]);

  return (
    <section>
      <h2>Dashboard</h2>
      <div style={containerStyle}>
        <div style={cardStyle}>
          <h3>Customers</h3>
          <p>{counts.customers}</p>
        </div>
        <div style={cardStyle}>
          <h3>Vehicles</h3>
          <p>{counts.vehicles}</p>
        </div>
        <div style={cardStyle}>
          <h3>Services</h3>
          <p>{counts.services}</p>
        </div>
      </div>
    </section>
  );
}

export default Dashboard;
