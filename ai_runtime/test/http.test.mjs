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

test('owner maintenance is private and draining preserves an active request', async t => {
  const token = 'm'.repeat(48); let finish;
  const server = runtimeServer({token,concurrency:1,timeoutMs:5000,maintenance:{status:async()=>({installed:'0.163.0'})},
    provider:{async run(){return new Promise(resolve=>{finish=()=>resolve({status:'completed'});});}}});
  server.listen(0,'127.0.0.1'); await once(server,'listening'); t.after(()=>server.close());
  const base=`http://127.0.0.1:${server.address().port}`,headers={Authorization:`Bearer ${token}`,'Content-Type':'application/json'};
  assert.equal((await fetch(base+'/v1/runtime')).status,401);
  assert.equal((await (await fetch(base+'/v1/runtime',{headers})).json()).installed,'0.163.0');
  const body=JSON.stringify({id:randomUUID(),messages:[],tools:[],maxInputTokens:100,maxOutputTokens:100,maxModelCalls:1,maxToolCalls:1});
  const active=await fetch(base+'/v1/jobs',{method:'POST',headers,body});
  const drain=await fetch(base+'/v1/drain',{method:'POST',headers,body:'{"enabled":true}'});
  assert.equal((await drain.json()).active,1);
  assert.equal((await fetch(base+'/v1/jobs',{method:'POST',headers,body})).status,503);
  finish(); assert.match(await active.text(),/completed/);
  assert.equal((await (await fetch(base+'/v1/health',{headers})).json()).active,0);
});
