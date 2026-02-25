import { privateKeyToAccount } from 'viem/accounts';
import {
    createPublicClient,
    createWalletClient,
    http,
    type WalletClient,
    type PublicClient,
} from 'viem';
import { StorageHubClient } from '@storagehub-sdk/core';
import { ApiPromise, WsProvider, Keyring } from '@polkadot/api';
import { types } from '@storagehub/types-bundle';
import { cryptoWaitReady } from '@polkadot/util-crypto';
import { NETWORK, chain } from '../config/networks.js';
import dotenv from 'dotenv';
dotenv.config();

const PRIVATE_KEY = process.env.PRIVATE_KEY!;

let account: ReturnType<typeof privateKeyToAccount>;
let address: string;
let signer: any;
let walletClient: WalletClient;
let publicClient: PublicClient;
let storageHubClient: StorageHubClient;
let polkadotApi: ApiPromise;

export async function initClients() {
    console.log('[ClientService] Initializing DataHaven clients...');

    // EVM account from private key
    account = privateKeyToAccount(`0x${PRIVATE_KEY}` as `0x${string}`);
    address = account.address;
    console.log(`[ClientService] Wallet address: ${address}`);

    // Substrate signer
    await cryptoWaitReady();
    const walletKeyring = new Keyring({ type: 'ethereum' });
    signer = walletKeyring.addFromUri(`0x${PRIVATE_KEY}`);

    // Viem clients
    walletClient = createWalletClient({
        chain,
        account,
        transport: http(NETWORK.rpcUrl),
    });

    publicClient = createPublicClient({
        chain,
        transport: http(NETWORK.rpcUrl),
    });

    // StorageHub client (high-level SDK)
    storageHubClient = new StorageHubClient({
        rpcUrl: NETWORK.rpcUrl,
        chain: chain,
        walletClient: walletClient,
        filesystemContractAddress: NETWORK.filesystemContractAddress,
    });

    // Polkadot API client (Substrate layer)
    const provider = new WsProvider(NETWORK.wsUrl);
    polkadotApi = await ApiPromise.create({
        provider,
        typesBundle: types,
        noInitWarn: true,
    });

    console.log('[ClientService] All clients initialized successfully.');
}

export function getAccount() { return account; }
export function getAddress() { return address; }
export function getSigner() { return signer; }
export function getWalletClient() { return walletClient; }
export function getPublicClient() { return publicClient; }
export function getStorageHubClient() { return storageHubClient; }
export function getPolkadotApi() { return polkadotApi; }
