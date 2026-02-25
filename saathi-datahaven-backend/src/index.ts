import '@storagehub/api-augment';
import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const CRASH_LOG = path.resolve(__dirname, '../../crash_logs.txt');

// Crash logger — appends to crash_logs.txt
function logCrash(layer: string, severity: string, message: string) {
    const timestamp = new Date().toISOString();
    const entry = `[${timestamp}] [${layer}] [${severity}] — ${message}\n`;
    console.error(entry.trim());
    try {
        fs.appendFileSync(CRASH_LOG, entry);
    } catch (_) {
        // If crash log write fails, just print to stderr
    }
}

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3001;

// ─── Health Check ──────────────────────────────────────
app.get('/health', (_req, res) => {
    res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// ─── Placeholder routes (Phase 2/3 will add real ones) ──
app.get('/api/status', (_req, res) => {
    res.json({
        message: 'saathi-datahaven-backend is running',
        phase: 1,
        features: ['health-check'],
    });
});

// ─── Global Error Handler ──────────────────────────────
app.use((err: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
    logCrash('BACKEND', 'ERROR', err.message);
    res.status(500).json({ success: false, error: err.message });
});

// ─── Start Server ──────────────────────────────────────
app.listen(PORT, () => {
    console.log(`\n🚀 saathi-datahaven-backend running on http://localhost:${PORT}`);
    console.log(`📋 Health:  GET http://localhost:${PORT}/health`);
    console.log(`📋 Status:  GET http://localhost:${PORT}/api/status\n`);
});
