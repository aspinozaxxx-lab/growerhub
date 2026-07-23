import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

export const GOOGLE_SCOPES = [
  'https://www.googleapis.com/auth/webmasters.readonly',
  'https://www.googleapis.com/auth/analytics.readonly',
];

export const resolveGoogleCredentialPaths = () => ({
  clientPath: process.env.GOOGLE_OAUTH_CLIENT_FILE
    || path.join(os.homedir(), '.secrets', 'growerhub', 'google-oauth-client.json'),
  tokenPath: process.env.GOOGLE_OAUTH_TOKEN_FILE
    || path.join(os.homedir(), '.secrets', 'growerhub', 'google-oauth-token.json'),
});

const readJson = (filePath, label) => {
  if (!fs.existsSync(filePath)) {
    throw new Error(`${label} не найден: ${filePath}`);
  }
  try {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch {
    throw new Error(`${label} содержит некорректный JSON: ${filePath}`);
  }
};

export const readGoogleOAuthClient = (clientPath) => {
  const source = readJson(clientPath, 'Файл OAuth-клиента Google');
  const client = source.installed;
  if (!client?.client_id || !client?.token_uri) {
    throw new Error('Нужен OAuth client типа Desktop app в поле installed');
  }
  return client;
};

export const writeGoogleOAuthToken = (tokenPath, value) => {
  fs.mkdirSync(path.dirname(tokenPath), { recursive: true });
  fs.writeFileSync(tokenPath, `${JSON.stringify(value, null, 2)}\n`, {
    encoding: 'utf8',
    mode: 0o600,
  });
};

const tokenError = async (response) => {
  const body = await response.text();
  try {
    const data = JSON.parse(body);
    return data.error_description || data.error || response.statusText;
  } catch {
    return response.statusText;
  }
};

export const exchangeAuthorizationCode = async ({
  client,
  code,
  codeVerifier,
  redirectUri,
}) => {
  const response = await fetch(client.token_uri, {
    method: 'POST',
    signal: AbortSignal.timeout(30000),
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: client.client_id,
      client_secret: client.client_secret || '',
      code,
      code_verifier: codeVerifier,
      grant_type: 'authorization_code',
      redirect_uri: redirectUri,
    }),
  });
  if (!response.ok) {
    throw new Error(`Google не выдал OAuth-токен: ${await tokenError(response)}`);
  }
  return response.json();
};

export const getGoogleAccessToken = async () => {
  const { tokenPath } = resolveGoogleCredentialPaths();
  const credential = readJson(tokenPath, 'Файл OAuth-токена Google');
  const response = await fetch(credential.token_uri || 'https://oauth2.googleapis.com/token', {
    method: 'POST',
    signal: AbortSignal.timeout(30000),
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: credential.client_id,
      client_secret: credential.client_secret || '',
      refresh_token: credential.refresh_token,
      grant_type: 'refresh_token',
    }),
  });
  if (!response.ok) {
    throw new Error(`Не удалось обновить OAuth-токен Google: ${await tokenError(response)}`);
  }
  const data = await response.json();
  if (!data.access_token) {
    throw new Error('Google не вернул access_token');
  }
  return data.access_token;
};
