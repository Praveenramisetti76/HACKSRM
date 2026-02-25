# 🔗 Minimal DataHaven Integration — Prescription Storage Only

## 🎯 ONE FEATURE: Upload prescription images to DataHaven testnet

Android → Backend (Node.js) → DataHaven SDK → MSP → On-chain

---

## Phase 1 — Backend Project Setup ✅
- Create `saathi-datahaven-backend/`
- `pnpm init`, `tsconfig.json`
- Install DataHaven SDK + Express + dependencies
- Create `.env` with provided keys
- Create folder structure
- **TEST:** `pnpm dev` compiles without errors

## Phase 2 — SDK Services + Bucket Logic
- `src/config/networks.ts` — testnet config
- `src/services/clientService.ts` — StorageHub + Polkadot + Viem clients
- `src/services/mspService.ts` — MSP client + SIWE auth
- On startup: check/create bucket
- **TEST:** Server boots, connects to DataHaven, bucket confirmed

## Phase 3 — Upload Endpoint
- `POST /upload-prescription` — multipart image upload
- Upload to DataHaven bucket via SDK
- Return `{ fileId, bucketId, transactionHash }`
- `GET /health` — health check
- **TEST:** Upload image via Postman, verify on-chain

## Phase 4 — Android Integration
- Retrofit API service
- On prescription upload → send to backend
- Save `fileId` in Room DB
- Show "Securely Stored on DataHaven"
- **TEST:** Full end-to-end from Android
