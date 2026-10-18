import test from 'node:test';
import assert from 'node:assert/strict';
import { CodexReleases, newer } from '../src/releases.mjs';

test('only newer stable releases produce a notification candidate', async () => {
  assert.equal(newer('0.164.0', '0.163.0'), true);
  assert.equal(newer('0.99.0', '0.163.0'), false);
  assert.equal(newer('0.163.0', '0.163.0'), false);
  assert.equal(newer('0.164.0-alpha', '0.163.0'), false);
  const check = new CodexReleases({version: async () => 'codex-cli 0.163.0\n', fetcher: async (url, options) => {
    assert.equal(url, 'https://registry.npmjs.org/@openai%2fcodex/latest'); assert.equal(options.redirect, 'error');
    return new Response(JSON.stringify({name:'@openai/codex',version:'0.164.0'}));
  }});
  assert.equal((await check.status()).updateAvailable, true);
});
test('registry errors and untrusted version strings never imply up-to-date or an update', async () => {
  for (const response of [new Response('failure', {status:503}), new Response(JSON.stringify({name:'elsewhere',version:'0.164.0'})), new Response(JSON.stringify({name:'@openai/codex',version:'1.0.0;command'}))]) {
    const status = await new CodexReleases({version:async()=> 'codex-cli 0.163.0',fetcher:async()=>response}).status();
    assert.equal(status.installed, '0.163.0'); assert.equal(status.error, 'check_unavailable'); assert.equal(status.updateAvailable, false);
  }
});
