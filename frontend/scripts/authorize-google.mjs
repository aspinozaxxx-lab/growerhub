import crypto from 'node:crypto';
import http from 'node:http';
import {
  exchangeAuthorizationCode,
  GOOGLE_SCOPES,
  readGoogleOAuthClient,
  resolveGoogleCredentialPaths,
  writeGoogleOAuthToken,
} from './lib/google-oauth.mjs';

const { clientPath, tokenPath } = resolveGoogleCredentialPaths();
const client = readGoogleOAuthClient(clientPath);
const state = crypto.randomBytes(24).toString('base64url');
const codeVerifier = crypto.randomBytes(64).toString('base64url');
const codeChallenge = crypto.createHash('sha256').update(codeVerifier).digest('base64url');

let finishAuthorization;
let failAuthorization;
const authorizationCode = new Promise((resolve, reject) => {
  finishAuthorization = resolve;
  failAuthorization = reject;
});

const server = http.createServer((request, response) => {
  const requestUrl = new URL(request.url || '/', 'http://127.0.0.1');
  if (requestUrl.pathname !== '/oauth2/callback') {
    response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end('Страница не найдена.');
    return;
  }
  if (requestUrl.searchParams.get('state') !== state) {
    response.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end('Не совпал параметр state. Вернитесь в терминал и повторите авторизацию.');
    failAuthorization(new Error('Google OAuth вернул неверный state'));
    return;
  }
  const error = requestUrl.searchParams.get('error');
  const code = requestUrl.searchParams.get('code');
  if (error || !code) {
    response.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end('Доступ не был подтверждён. Можно закрыть это окно.');
    failAuthorization(new Error(`Google OAuth не завершён: ${error || 'нет кода'}`));
    return;
  }
  response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  response.end('<!doctype html><meta charset="utf-8"><title>GrowerHub</title><h1>Доступ подтверждён</h1><p>Можно закрыть это окно и вернуться в терминал.</p>');
  finishAuthorization(code);
});

await new Promise((resolve, reject) => {
  server.once('error', reject);
  server.listen(0, '127.0.0.1', resolve);
});

const address = server.address();
if (!address || typeof address === 'string') {
  server.close();
  throw new Error('Не удалось открыть локальный OAuth callback');
}
const redirectUri = `http://127.0.0.1:${address.port}/oauth2/callback`;
const authorizationUrl = new URL(client.auth_uri || 'https://accounts.google.com/o/oauth2/v2/auth');
authorizationUrl.search = new URLSearchParams({
  access_type: 'offline',
  client_id: client.client_id,
  code_challenge: codeChallenge,
  code_challenge_method: 'S256',
  include_granted_scopes: 'true',
  prompt: 'consent',
  redirect_uri: redirectUri,
  response_type: 'code',
  scope: GOOGLE_SCOPES.join(' '),
  state,
}).toString();

console.log('Откройте ссылку в браузере и подтвердите доступ только на чтение:');
console.log(authorizationUrl.toString());
console.log('\nОжидание ответа Google — до 5 минут...');

const timeout = setTimeout(() => {
  failAuthorization(new Error('Время ожидания Google OAuth истекло'));
}, 300000);

try {
  const code = await authorizationCode;
  const tokens = await exchangeAuthorizationCode({
    client,
    code,
    codeVerifier,
    redirectUri,
  });
  if (!tokens.refresh_token) {
    throw new Error('Google не вернул refresh_token; удалите старый доступ приложения и повторите');
  }
  writeGoogleOAuthToken(tokenPath, {
    type: 'authorized_user',
    client_id: client.client_id,
    client_secret: client.client_secret || '',
    refresh_token: tokens.refresh_token,
    token_uri: client.token_uri || 'https://oauth2.googleapis.com/token',
    scopes: GOOGLE_SCOPES,
    created_at: new Date().toISOString(),
  });
  console.log(`OAuth-доступ сохранён: ${tokenPath}`);
} finally {
  clearTimeout(timeout);
  await new Promise((resolve) => server.close(resolve));
}
