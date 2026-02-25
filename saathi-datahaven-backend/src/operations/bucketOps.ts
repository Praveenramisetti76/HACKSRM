import {
    getStorageHubClient,
    getAddress,
    getPublicClient,
    getPolkadotApi,
} from '../services/clientService.js';
import {
    getMspInfo,
    getValueProps,
    getMspClient,
} from '../services/mspService.js';

/**
 * Create a bucket on DataHaven.
 * Follows the official SDK docs exactly:
 * 1. Get MSP info + value prop
 * 2. Derive bucket ID
 * 3. Check if bucket already exists
 * 4. Create on-chain
 * 5. Wait for tx receipt
 */
export async function createBucket(bucketName: string) {
    const storageHubClient = getStorageHubClient();
    const address = getAddress();
    const publicClient = getPublicClient();
    const polkadotApi = getPolkadotApi();

    // Get MSP info
    const { mspId } = await getMspInfo();

    // Get a value prop (storage fee)
    const valuePropId = await getValueProps();
    console.log(`[BucketOps] Value Prop ID: ${valuePropId}`);

    // Derive deterministic bucket ID
    const bucketId = (await storageHubClient.deriveBucketId(
        address,
        bucketName,
    )) as string;
    console.log(`[BucketOps] Derived bucket ID: ${bucketId}`);

    // Check if bucket already exists on-chain
    const bucketBeforeCreation =
        await polkadotApi.query.providers.buckets(bucketId);

    if (!bucketBeforeCreation.isEmpty) {
        console.log(`[BucketOps] Bucket already exists: ${bucketId}`);
        return { bucketId, alreadyExisted: true };
    }

    console.log('[BucketOps] Bucket does not exist yet, creating...');

    const isPrivate = false;

    // Create bucket on chain
    const txHash: `0x${string}` | undefined = await storageHubClient.createBucket(
        mspId as `0x${string}`,
        bucketName,
        isPrivate,
        valuePropId,
    );

    console.log(`[BucketOps] createBucket txHash: ${txHash}`);
    if (!txHash) {
        throw new Error('createBucket() did not return a transaction hash');
    }

    // Wait for transaction receipt
    const txReceipt = await publicClient.waitForTransactionReceipt({
        hash: txHash,
    });
    if (txReceipt.status !== 'success') {
        throw new Error(`Bucket creation failed: ${txHash}`);
    }

    console.log('[BucketOps] Bucket created successfully on-chain!');
    return { bucketId, txReceipt, alreadyExisted: false };
}

/**
 * Verify that a bucket exists on-chain and return its data.
 */
export async function verifyBucketCreation(bucketId: string) {
    const polkadotApi = getPolkadotApi();
    const address = getAddress();
    const { mspId } = await getMspInfo();

    const bucket = await polkadotApi.query.providers.buckets(bucketId);
    if (bucket.isEmpty) {
        throw new Error('Bucket not found on chain after creation');
    }

    const bucketData = bucket.unwrap().toHuman();
    console.log(
        `[BucketOps] Bucket owner matches: ${(bucketData as any).userId === address}`,
    );
    console.log(
        `[BucketOps] Bucket MSP matches: ${(bucketData as any).mspId === mspId}`,
    );
    return bucketData;
}

/**
 * Poll MSP backend until the indexer has caught up with the bucket.
 * Prevents upload failures due to race condition.
 */
export async function waitForBackendBucketReady(bucketId: string) {
    const mspClient = getMspClient();
    const maxAttempts = 10;
    const delayMs = 2000;

    for (let i = 0; i < maxAttempts; i++) {
        console.log(
            `[BucketOps] Checking MSP backend for bucket, attempt ${i + 1}/${maxAttempts}...`,
        );
        try {
            const bucket = await mspClient.buckets.getBucket(bucketId);
            if (bucket) {
                console.log('[BucketOps] Bucket found in MSP backend:', bucket);
                return bucket;
            }
        } catch (error: any) {
            if (error?.status === 404 || error?.body?.error === 'Not found: Record') {
                console.log('[BucketOps] Bucket not indexed yet (404).');
            } else {
                console.log('[BucketOps] Unexpected error:', error);
                throw error;
            }
        }
        await new Promise((r) => setTimeout(r, delayMs));
    }
    throw new Error(`Bucket ${bucketId} not found in MSP backend after ${maxAttempts} attempts`);
}
