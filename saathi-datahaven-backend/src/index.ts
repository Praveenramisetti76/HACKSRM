import '@storagehub/api-augment';
import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

import { initClients, getAddress } from './services/clientService.js';
import { initMspService, getMspHealth, authenticateUser } from './services/mspService.js';
import { createBucket, verifyBucketCreation, waitForBackendBucketReady } from './operations/bucketOps.js';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const CRASH_LOG = path.resolve(__dirname, '../../crash_logs.txt');

// ─── Crash Logger ──────────────────────────────────────
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
const BUCKET_NAME = process.env.BUCKET_NAME || 'sahay-prescriptions';

// Global state — set during initialization
let isReady = false;
let bucketId: string | null = null;
let initError: string | null = null;

// ─── Health Check ──────────────────────────────────────
app.get('/health', (_req, res) => {
    res.json({
        status: isReady ? 'ok' : 'initializing',
        timestamp: new Date().toISOString(),
        bucketReady: !!bucketId,
        walletAddress: isReady ? getAddress() : null,
    });
});

// ─── Status ────────────────────────────────────────────
app.get('/api/status', (_req, res) => {
    res.json({
        message: 'saathi-datahaven-backend is running',
        phase: 2,
        ready: isReady,
        bucketId: bucketId,
        bucketName: BUCKET_NAME,
        error: initError,
    });
});

// ─── Global Error Handler ──────────────────────────────
app.use((err: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
    logCrash('BACKEND', 'ERROR', err.message);
    res.status(500).json({ success: false, error: err.message });
});

// ─── DataHaven Initialization ──────────────────────────
async function initializeDataHaven() {
    console.log('\n═══════════════════════════════════════════');
    console.log('  🔗 Initializing DataHaven Connection...');
    console.log('═══════════════════════════════════════════\n');

    // Step 1: Initialize SDK clients (EVM + Substrate)
    console.log('📡 Step 1/5: Initializing SDK clients...');
    await initClients();
    console.log(`   ✅ Wallet: ${getAddress()}`);

    // Step 2: Connect to MSP
    console.log('📡 Step 2/5: Connecting to MSP...');
    await initMspService();

    // Step 3: Check MSP health
    console.log('📡 Step 3/5: Checking MSP health...');
    const health = await getMspHealth();
    console.log(`   ✅ MSP Health: ${health}`);

    // Step 4: Authenticate via SIWE
    console.log('📡 Step 4/5: Authenticating via SIWE...');
    const profile = await authenticateUser();
    console.log(`   ✅ Authenticated as: ${JSON.stringify(profile)}`);

    // Step 5: Create or verify bucket
    console.log(`📡 Step 5/5: Setting up bucket "${BUCKET_NAME}"...`);
    const result = await createBucket(BUCKET_NAME);
    bucketId = result.bucketId;

    if (result.alreadyExisted) {
        console.log(`   ✅ Bucket already exists: ${bucketId}`);
    } else {
        console.log(`   ✅ Bucket created: ${bucketId}`);
        console.log(`   📦 Tx receipt: ${JSON.stringify(result.txReceipt?.transactionHash)}`);
    }

    // Verify on-chain
    const bucketData = await verifyBucketCreation(bucketId);
    console.log(`   ✅ Bucket verified on-chain:`, bucketData);

    // Wait for MSP backend indexer
    console.log('   ⏳ Waiting for MSP backend to index bucket...');
    await waitForBackendBucketReady(bucketId);

    isReady = true;
    console.log('\n═══════════════════════════════════════════');
    console.log('  ✅ DataHaven Ready! Bucket:', bucketId);
    console.log('═══════════════════════════════════════════\n');
}

// ─── Start Server ──────────────────────────────────────
app.listen(PORT, () => {
    console.log(`\n🚀 saathi-datahaven-backend running on http://localhost:${PORT}`);
    console.log(`📋 Health:  GET http://localhost:${PORT}/health`);
    console.log(`📋 Status:  GET http://localhost:${PORT}/api/status\n`);

    // Initialize DataHaven in the background (don't block Express)
    initializeDataHaven().catch((err) => {
        initError = err.message;
        logCrash('DATAHAVEN_INIT', 'FATAL', `Initialization failed: ${err.message}\n${err.stack}`);
        console.error('\n❌ DataHaven initialization failed:', err.message);
        console.error('   Server is running but storage features are disabled.');
        console.error('   Check crash_logs.txt for details.\n');
    });
});
