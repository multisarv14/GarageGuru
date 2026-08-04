# GarageGuruApp

GarageGuruApp is a simple garage management application with a Node.js/Express backend and a React frontend. The backend uses in-memory storage, so data resets whenever the server restarts.

## Project Structure

```text
GarageGuruApp/
├── backend/
│   ├── package.json
│   └── src/
│       ├── db.js
│       ├── index.js
│       └── routes/
│           ├── customers.js
│           ├── services.js
│           └── vehicles.js
├── frontend/
│   ├── package.json
│   ├── public/
│   │   └── index.html
│   └── src/
│       ├── App.js
│       ├── index.js
│       └── components/
│           ├── Customers.js
│           ├── Dashboard.js
│           ├── Services.js
│           └── Vehicles.js
└── README.md
```

## Backend Setup

1. Navigate to the backend folder:
   ```bash
   cd backend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start the backend server:
   ```bash
   npm start
   ```

The backend runs on `http://localhost:5000` by default.

### API Endpoints

- `GET /api/dashboard`
- `GET, POST /api/customers`
- `GET, PUT, DELETE /api/customers/:id`
- `GET, POST /api/vehicles`
- `GET, PUT, DELETE /api/vehicles/:id`
- `GET, POST /api/services`
- `GET, PUT, DELETE /api/services/:id`

## Frontend Setup

1. Navigate to the frontend folder:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start the frontend app:
   ```bash
   npm start
   ```

The frontend runs on `http://localhost:3000` by default and expects the backend API at `http://localhost:5000/api`.

## Notes

- The frontend uses React functional components with hooks.
- The backend stores data only in memory.
- No database configuration is required for this initial structure.
