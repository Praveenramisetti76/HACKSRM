import { createReadStream, statSync } from 'node:fs';
import { Readable } from 'node:stream';
import { FileManager, ReplicationLevel } from '@storagehub-sdk/core';
import { TypeRegistry } from '@polkadot/types';
import type { AccountId20, H256 } from '@polkadot/types/interfaces';
import type { PalletFileSystemStorageRequestMetadata } from '@polkadot/types/lookup';

import {
    getStorageHubClient,
    getAddress,
    getPublicClient,
    getPolkadotApi,
    getAccount,
} from '../services/clientService.js';
import {
    getMspClient,
    getMspInfo,
    authenticateUser,
} from '../services/mspService.js';

/**
 * Extract libp2p peer IDs from multiaddresses.
 */
function extractPeerIDs(multiaddresses: string[]): string[] {
    return (multiaddresses ?? [])
        .map((addr) => addr.split('/p2p/').pop())
        .filter((id): id is string => !!id);
}

/**
 * Upload a file to DataHaven.
 * Follows the official SDK docs exactly:
 * 1. Issue storage request on-chain
 * 2. Wait for tx receipt
 * 3. Verify storage request on-chain
 * 4. Authenticate with MSP
 * 5. Upload file blob to MSP
 */
export async function uploadFile(
    bucketId: string,
    filePath: string,
    fileName: string,
) {
    const storageHubClient = getStorageHubClient();
    const address = getAddress();
    const publicClient = getPublicClient();
    const polkadotApi = getPolkadotApi();
    const account = getAccount();
    const mspClient = getMspClient();

    console.log(`[FileOps] Starting upload: ${fileName} → bucket ${bucketId}`);

    // Set up FileManager
    const fileSize = statSync(filePath).size;
    const fileManager = new FileManager({
        size: fileSize,
        stream: () => Readable.toWeb(createReadStream(filePath)) as ReadableStream<Uint8Array>,
    });

    // Get file fingerprint
    const fingerprint = await fileManager.getFingerprint();
    console.log(`[FileOps] Fingerprint: ${fingerprint.toHex()}`);
    const fileSizeBigInt = BigInt(fileManager.getFileSize());
    console.log(`[FileOps] File size: ${fileSize} bytes`);

    // Get MSP details
    const { mspId, multiaddresses } = await getMspInfo();
    if (!multiaddresses?.length) {
        throw new Error('MSP multiaddresses are missing');
    }
    const peerIds: string[] = extractPeerIDs(multiaddresses);
    if (peerIds.length === 0) {
        throw new Error('MSP multiaddresses had no /p2p/<peerId> segment');
    }

    // Issue storage request on-chain
    const replicationLevel = ReplicationLevel.Custom;
    const replicas = 1;

    console.log('[FileOps] Issuing storage request on-chain...');
    const txHash: `0x${string}` | undefined = await storageHubClient.issueStorageRequest(
        bucketId as `0x${string}`,
        fileName,
        fingerprint.toHex() as `0x${string}`,
        fileSizeBigInt,
        mspId as `0x${string}`,
        peerIds,
        replicationLevel,
        replicas,
    );

    console.log(`[FileOps] issueStorageRequest txHash: ${txHash}`);
    if (!txHash) {
        throw new Error('issueStorageRequest() did not return a transaction hash');
    }

    // Wait for transaction receipt
    const receipt = await publicClient.waitForTransactionReceipt({ hash: txHash });
    if (receipt.status !== 'success') {
        throw new Error(`Storage request failed: ${txHash}`);
    }
    console.log('[FileOps] Storage request confirmed on-chain.');

    // Compute file key
    const registry = new TypeRegistry();
    const owner = registry.createType('AccountId20', account.address) as AccountId20;
    const bucketIdH256 = registry.createType('H256', bucketId) as H256;
    const fileKey = await fileManager.computeFileKey(owner, bucketIdH256, fileName);
    console.log(`[FileOps] File key: ${fileKey.toHex()}`);

    // Verify storage request exists on-chain
    const storageRequest = await polkadotApi.query.fileSystem.storageRequests(fileKey);
    if (!storageRequest.isSome) {
        throw new Error('Storage request not found on chain');
    }
    console.log('[FileOps] Storage request verified on-chain.');

    // Authenticate with MSP before upload
    console.log('[FileOps] Authenticating with MSP...');
    await authenticateUser();

    // Upload file to MSP
    console.log('[FileOps] Uploading file to MSP...');
    const uploadReceipt = await mspClient.files.uploadFile(
        bucketId,
        fileKey.toHex(),
        await fileManager.getFileBlob(),
        address,
        fileName,
    );

    console.log(`[FileOps] Upload status: ${uploadReceipt.status}`);
    if (uploadReceipt.status !== 'upload_successful') {
        throw new Error(`File upload to MSP failed with status: ${uploadReceipt.status}`);
    }

    console.log('[FileOps] ✅ File uploaded successfully!');
    return {
        fileKey: fileKey.toHex(),
        uploadReceipt,
        txHash,
        fingerprint: fingerprint.toHex(),
        fileSize,
    };
}

/**
 * Poll until MSP has confirmed the storage request on-chain.
 */
export async function waitForMSPConfirmOnChain(fileKey: string) {
    const polkadotApi = getPolkadotApi();
    const maxAttempts = 20;
    const delayMs = 2000;

    for (let i = 0; i < maxAttempts; i++) {
        console.log(`[FileOps] Checking MSP on-chain confirm, attempt ${i + 1}/${maxAttempts}...`);

        const req = await polkadotApi.query.fileSystem.storageRequests(fileKey);
        if (req.isNone) {
            throw new Error(`StorageRequest for ${fileKey} no longer exists on-chain.`);
        }

        const data: PalletFileSystemStorageRequestMetadata = req.unwrap();
        const mspStatus = data.mspStatus;
        console.log(`[FileOps] MSP status: ${mspStatus.type}`);

        const mspConfirmed = mspStatus.isAcceptedNewFile || mspStatus.isAcceptedExistingFile;
        if (mspConfirmed) {
            console.log('[FileOps] ✅ MSP confirmed storage request on-chain.');
            return;
        }

        await new Promise((r) => setTimeout(r, delayMs));
    }
    throw new Error('Timed out waiting for MSP confirmation on-chain');
}

/**
 * Poll MSP backend until file metadata is available.
 */
export async function waitForBackendFileReady(bucketId: string, fileKey: string) {
    const mspClient = getMspClient();
    const maxAttempts = 30;  // ~60 seconds
    const delayMs = 2000;

    for (let i = 0; i < maxAttempts; i++) {
        console.log(`[FileOps] Checking MSP backend for file, attempt ${i + 1}/${maxAttempts}...`);

        try {
            const fileInfo = await mspClient.files.getFileInfo(bucketId, fileKey);

            if (fileInfo.status === 'ready') {
                console.log('[FileOps] ✅ File ready in MSP backend:', fileInfo);
                return fileInfo;
            }
            if (fileInfo.status === 'revoked') throw new Error('File upload cancelled');
            if (fileInfo.status === 'rejected') throw new Error('File rejected by MSP');
            if (fileInfo.status === 'expired') throw new Error('Storage request expired');

            console.log(`[FileOps] File status: "${fileInfo.status}", waiting...`);
        } catch (error: any) {
            if (error?.status === 404 || error?.body?.error === 'Not found: Record') {
                console.log('[FileOps] File not indexed yet (404).');
            } else {
                throw error;
            }
        }
        await new Promise((r) => setTimeout(r, delayMs));
    }
    throw new Error('Timed out waiting for file to be ready in MSP backend');
}
