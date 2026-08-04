import React, { useMemo, useState } from 'react';
import Dashboard from './components/Dashboard';
import Customers from './components/Customers';
import Vehicles from './components/Vehicles';
import Services from './components/Services';

const navItems = ['Dashboard', 'Customers', 'Vehicles', 'Services'];

const appStyles = {
  fontFamily: 'Arial, sans-serif',
  maxWidth: '1100px',
  margin: '0 auto',
  padding: '24px',
  color: '#111827',
};

const navStyles = {
  display: 'flex',
  gap: '12px',
  marginBottom: '24px',
  flexWrap: 'wrap',
};

const buttonStyles = (active) => ({
  padding: '10px 16px',
  borderRadius: '8px',
  border: '1px solid #d1d5db',
  backgroundColor: active ? '#2563eb' : '#ffffff',
  color: active ? '#ffffff' : '#111827',
  cursor: 'pointer',
});

function App() {
  const [activeView, setActiveView] = useState('Dashboard');
  const apiBaseUrl = useMemo(() => 'http://localhost:5000/api', []);

  return (
    <div style={appStyles}>
      <header>
        <h1>GarageGuruApp</h1>
        <p>Manage customers, vehicles, and service records for your garage.</p>
      </header>

      <nav style={navStyles}>
        {navItems.map((item) => (
          <button
            key={item}
            type="button"
            style={buttonStyles(activeView === item)}
            onClick={() => setActiveView(item)}
          >
            {item}
          </button>
        ))}
      </nav>

      {activeView === 'Dashboard' && <Dashboard apiBaseUrl={apiBaseUrl} />}
      {activeView === 'Customers' && <Customers apiBaseUrl={apiBaseUrl} />}
      {activeView === 'Vehicles' && <Vehicles apiBaseUrl={apiBaseUrl} />}
      {activeView === 'Services' && <Services apiBaseUrl={apiBaseUrl} />}
    </div>
  );
}

export default App;
