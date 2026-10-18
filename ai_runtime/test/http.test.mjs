import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { randomUUID } from 'node:crypto';
import { runtimeServer } from '../src/http.mjs';

test('requires service authentication and never repeats completed jobs', async t => {
  let runs = 0;
  const token = 't'.repeat(48);
  const server = runtimeServer({ token, concurrency: 1, timeoutMs: 1000,
    provider: { async run() { runs++; return { status: 'completed', usageKnown: true }; } } });
  server.listen(0, '127.0.0.1'); await once(server, 'listening'); t.after(() => server.close());
  const base = `http://127.0.0.1:${server.address().port}`;
  assert.equal((await fetch(base + '/v1/health')).status, 401);
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
  assert.equal((await fetch(base + '/v1/health?token=bad', { headers })).status, 400);
  const body = JSON.stringify({ id: randomUUID(), messages: [], tools: [], maxInputTokens: 100, maxOutputTokens: 100, maxModelCalls: 1, maxToolCalls: 1 });
  const first = await fetch(base + '/v1/jobs', { method: 'POST', headers, body });
  assert.equal(first.status, 200); await first.text();
  assert.equal((await fetch(base + '/v1/jobs', { method: 'POST', headers, body })).status, 409);
  assert.equal(runs, 1);
});
