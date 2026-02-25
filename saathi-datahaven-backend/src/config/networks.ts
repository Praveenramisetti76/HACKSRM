import { Chain, defineChain } from 'viem';
import dotenv from 'dotenv';
dotenv.config();

export const NETWORK = {
    id: 55931,
    name: 'DataHaven Testnet',
    rpcUrl: process.env.RPC_URL || 'https://services.datahaven-testnet.network/testnet',
    wsUrl: 'wss://services.datahaven-testnet.network/testnet',
    mspUrl: process.env.MSP_BACKEND_URL || 'https://deo-dh-backend.testnet.datahaven-infra.network/',
    nativeCurrency: { name: 'Mock', symbol: 'MOCK', decimals: 18 },
    filesystemContractAddress:
        '0x0000000000000000000000000000000000000404' as `0x${string}`,
};

export const chain: Chain = defineChain({
    id: NETWORK.id,
    name: NETWORK.name,
    nativeCurrency: NETWORK.nativeCurrency,
    rpcUrls: { default: { http: [NETWORK.rpcUrl] } },
});
