# DataHaven Backend Tech Spec: Prescription Storage Integration

This document outlines the technical implementation details of the `saathi-datahaven-backend` service, which bridges the SAHAY Android application with the **DataHaven Decentralized Storage Network**.

## 🏗 System Architecture

The backend acts as a secure coordinator between the mobile app and the DataHaven protocol. It handles blockchain interactions (transactions), decentralized authentication (SIWE/MSP), and bucket management.

### Key Components:
- **Express Server**: Provides REST endpoints for the Android app.
- **StorageHub SDK**: Orchestrates communication with DataHaven MSPs (Managed Storage Providers).
- **Viem/Polkadot API**: Manages on-chain storage requests and transaction verification.

---

## 🔑 Authentication & Identity (Web3)

The backend uses a dedicated Ethereum/Polkadot-compatible account for on-chain interactions.

- **Wallet Address**: `0x91489B4d7504961eab3Bbf81C0EA32B06B994665`
- **Identity Method**: SIWE (Sign-In with Ethereum) for authentication with MSP backends.
- **On-chain Role**: Acts as the "Storage User" who issues storage requests for prescriptions.
- **MSP Session**: Authentication tokens are cached per session to avoid redundant signing.

---

## 🗄 Storage Infrastructure

We utilize a dedicated bucket on DataHaven for the SAATHI project.

- **Bucket Name**: `sahay-prescriptions`
- **Bucket ID**: `0x5a49e7f38ecc8f3d8dd89dbf56a33e1c39066a899c794295341b6012d5b4e57f`
- **MSP ID**: `1` (StorageHub Testnet MSP)
- **Value Proposition ID**: `0x628a23...da1e`
    - **Replication**: 3 replicas.
    - **Duration**: Permanent / Long-term.
    - **Privacy**: Currently configured as **Public** for easier verification, but supports E2E encryption.

---

## 🧬 Deep Dive: The 5-Step DataHaven Protocol

Every prescription upload follows a strict decentralized protocol to ensure data integrity and availability.

### 1. Cryptographic Fingerprinting
We generate a `fingerprint` (hash) of the file using the StorageHub `FileManager`. This fingerprint is used as the persistent "Proof of Data" on the blockchain.

### 2. Issuing Storage Requests (On-Chain)
The backend executes a transaction on the **DataHaven Runtime** via the `@polkadot/api`. This transaction locks the storage intent into the blockchain ledger, specifying the bucket, fingerprint, and desired MSP.

### 3. SIWE Authentication
To upload the actual blob, the backend signs a "Sign-In with Ethereum" message. This proves ownership of the bucket to the MSP without revealing private keys.

### 4. Blob Upload (Off-Chain)
The actual file content is streamed to the MSP's storage nodes. The MSP verifies that the blob matches the `fingerprint` registered in the storage request in Step 2.

### 5. Multi-Layer Verification
- **Runtime Confirmation**: We poll the blockchain state until the Storage Request status changes to `Confirmed`.
- **MSP Indexing**: We poll the MSP backend API until the file is indexed and ready for retrieval.

---

## 📡 API Endpoints

### 1. `POST /api/upload-prescription`
- **Input**: Multipart form-data with `file`.
- **Latency**: ~45-60 Seconds. This is a "Heavy" operation because it waits for blockchain block finality.
- **Response**: Returns a `fileKey` which is the on-chain reference used to fetch the data later.

### 2. `GET /api/status`
- **Purpose**: Health check for the Android app.
- **Response**: `{ "ready": true, "phase": 3, "bucketId": "..." }`

---

## 🛠 File Structure & Module Roles

| File | Responsibility |
|:---|:---|
| `src/index.ts` | Server entry point; handles server-side bucket verification on reboot. |
| `src/operations/fileOps.ts` | **The Core Protocol**: Implements the 5-step logic and polling loops. |
| `src/services/mspService.ts` | Manages MSP connections, ValueProps, and SIWE auth tokens. |
| `src/services/clientService.ts` | Bootstraps the Web3 stack (Viem, Polkadot, StorageHub). |
| `src/operations/bucketOps.ts` | Logic to find or create the `sahay-prescriptions` bucket. |

---

## ⚠️ Important Implementation Notes

1. **Transaction Nonces**: The backend handles high-concurrency by letting the Polkadot API manage nonces.
2. **Connectivity**: Android Emulators MUST use **`10.0.2.2:3001`** to reach this server.
3. **Idempotency**: The system checks if a bucket already exists before trying to create one, saving gas and time.
4. **Crash Logging**: Comprehensive logs are written to `crash_logs.txt` and `combined.log` for debugging production issues.

