import {
    type HealthStatus,
    type InfoResponse,
    MspClient,
    type UserInfo,
    type ValueProp,
} from '@storagehub-sdk/msp-client';
import { type HttpClientConfig } from '@storagehub-sdk/core';
import { getAddress, getWalletClient } from './clientService.js';
import { NETWORK } from '../config/networks.js';

let mspClient: MspClient;
let sessionToken: string | undefined = undefined;

export async function initMspService() {
    console.log('[MspService] Connecting to MSP...');

    const httpCfg: HttpClientConfig = { baseUrl: NETWORK.mspUrl };

    const address = getAddress();

    const sessionProvider = async () =>
        sessionToken
            ? ({ token: sessionToken, user: { address: address } } as const)
            : undefined;

    mspClient = await MspClient.connect(httpCfg, sessionProvider);
    console.log('[MspService] MSP client connected.');
}

export async function getMspInfo(): Promise<InfoResponse> {
    const mspInfo = await mspClient.info.getInfo();
    console.log(`[MspService] MSP ID: ${mspInfo.mspId}`);
    return mspInfo;
}

export async function getMspHealth(): Promise<HealthStatus> {
    const mspHealth = await mspClient.info.getHealth();
    console.log(`[MspService] MSP Health: ${mspHealth}`);
    return mspHealth;
}

export async function authenticateUser(): Promise<UserInfo> {
    console.log('[MspService] Authenticating via SIWE...');

    const walletClient = getWalletClient();
    const domain = 'localhost';
    const uri = 'http://localhost';

    const siweSession = await mspClient.auth.SIWE(walletClient, domain, uri);
    sessionToken = (siweSession as { token: string }).token;
    console.log('[MspService] SIWE authentication successful.');

    const profile: UserInfo = await mspClient.auth.getProfile();
    return profile;
}

// Retrieve MSP value propositions and select one for bucket creation
export async function getValueProps(): Promise<`0x${string}`> {
    const valueProps: ValueProp[] = await mspClient.info.getValuePropositions();
    if (!Array.isArray(valueProps) || valueProps.length === 0) {
        throw new Error('No value propositions available from MSP');
    }
    const valuePropId = valueProps[0].id as `0x${string}`;
    console.log(`[MspService] Chose Value Prop ID: ${valuePropId}`);
    return valuePropId;
}

export function getMspClient() { return mspClient; }
