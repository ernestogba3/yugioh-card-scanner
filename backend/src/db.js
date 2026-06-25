import 'dotenv/config';
import pg from 'pg';

if (!process.env.DATABASE_URL) {
    throw new Error('Falta DATABASE_URL. Copia .env.example a .env y rellénalo.');
}

// Pool de conexiones reutilizable en toda la app.
export const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL });
