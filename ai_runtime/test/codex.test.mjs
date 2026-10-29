import test from 'node:test';
import assert from 'node:assert/strict';
import { CodexProvider } from '../src/codex.mjs';

class FakeRpc {
  constructor(mode) { this.mode = mode; this.requests = []; this.listeners = []; this.results = []; }
  on(listener) { this.listeners.push(listener); }
  notify() {}
  close() { this.closed = true; }
  reject(id) { this.rejected = id; }
  respond(id, result) {
    this.results.push({ id, result });
    if (this.mode === 'contextAfterTool') {
      this.event('thread/tokenUsage/updated', { tokenUsage: { total: { inputTokens: 16001, outputTokens: 30 }, last: { inputTokens: 16001, outputTokens: 30 } } });
      return;
    }
    this.event('turn/completed', { turn: { status: 'completed' } });
  }
  event(method, params, id) { this.listeners.forEach(l => l({ method, params: { threadId: 't1', ...params }, ...(id === undefined ? {} : { id }) })); }
  async request(method, params) {
    this.requests.push({ method, params });
    if (method === 'initialize') return {};
    if (method === 'model/list') return { data: [{ model: 'test-model', displayName: 'Test', supportedReasoningEfforts: [{ reasoningEffort: 'medium' }],
      defaultReasoningEffort: 'medium', inputModalities: ['text', 'image'], isDefault: true }] };
    if (method === 'thread/start') return { thread: { id: 't1' } };
    if (method === 'turn/start') {
      queueMicrotask(() => {
        if (this.mode === 'last') for (let n=1;n<=3;n++) this.event('thread/tokenUsage/updated', {tokenUsage:{total:{inputTokens:n*100,outputTokens:n*10},last:{inputTokens:100,outputTokens:10}}});
        if (this.mode !== 'contextAfterTool') this.event('item/agentMessage/delta', { delta: 'Готово' });
        this.event('item/tool/call', { callId: 'call1', tool: this.mode === 'bad' ? 'exec_command' : 'gh_farm_report', arguments: {} }, 40);
      });
      return { turn: { id: 'turn1' } };
    }
    throw new Error('unexpected_rpc');
  }
}
const job = { model: 'test-model', effort: 'medium', maxInputTokens: 16000, maxOutputTokens: 4096, maxModelCalls: 3, maxToolCalls: 6,
  messages: [{ role: 'system', content: [{ type: 'text', text: 'Помощник GrowerHub' }] }, { role: 'user', content: [{ type: 'text', text: 'Как растения?' }] }],
  tools: [{ type: 'function', name: 'gh_farm_report', description: 'read', parameters: { type: 'object' } }] };
test('Codex exposes only domain tools, uses isolated ephemeral threads and verified model choices', async () => {
  const instances = []; const provider = new CodexProvider({ cwd: '/empty', startRpc: () => { const rpc = new FakeRpc(); instances.push(rpc); return rpc; } });
  let executed = 0;
  const outcome = await provider.run(job, { signal: new AbortController().signal, emit() {}, tool: async () => { executed++; return { farmCount: 1 }; } });
  assert.equal(outcome.status, 'completed'); assert.equal(executed, 1); assert.equal(outcome.text, 'Готово');
  const thread = instances[1].requests.find(r => r.method === 'thread/start').params;
  assert.equal(thread.ephemeral, true); assert.deepEqual(thread.environments, []); assert.equal(thread.sandbox, 'read-only');
  assert.deepEqual(thread.dynamicTools.map(t => t.name), ['gh_farm_report']); assert.ok(instances.every(r => r.closed));
  const invalid = await provider.run({ ...job, effort: 'invented' }, { signal: new AbortController().signal, emit() {}, tool() {} });
  assert.equal(invalid.code, 'model_rejected'); assert.equal(instances.length, 2);
});
test('Codex preserves the final tool result without starting another generation', async () => {
  const instances = []; let executed = 0;
  const provider = new CodexProvider({cwd:'/empty',startRpc:()=>{ const rpc=new FakeRpc('last'); instances.push(rpc); return rpc; }});
  const outcome = await provider.run(job,{signal:new AbortController().signal,emit(){},tool:async()=>{executed++; return {id:'proposal',status:'pending'};}});
  assert.equal(executed,1); assert.equal(outcome.status,'incomplete'); assert.equal(outcome.code,null);
  assert.equal(instances[1].results.length,0); assert.ok(instances.every(r=>r.closed));
});
test('Unexpected Codex RPC never reaches a backend tool', async () => {
  const instances = []; const provider = new CodexProvider({ cwd: '/empty', startRpc: () => { const rpc = new FakeRpc('bad'); instances.push(rpc); return rpc; } });
  const outcome = await provider.run(job, { signal: new AbortController().signal, emit() {}, tool() { assert.fail('must not run'); } });
  assert.equal(outcome.code, 'tool_rejected'); assert.equal(instances[1].rejected, 40); assert.ok(instances[1].closed);
});

test('limit posle podgotovki bez teksta ostavlyaet incomplete i ne povtoryaet instrument', async () => {
  const instances = []; let tools = 0;
  const provider = new CodexProvider({ cwd: '/empty', startRpc: () => { const rpc = new FakeRpc('contextAfterTool'); instances.push(rpc); return rpc; } });
  const result = await provider.run(job, { signal: new AbortController().signal, emit() {}, tool: async () => { tools++; return { id: 'prepared', status: 'pending' }; } });
  assert.equal(result.status, 'incomplete'); assert.equal(result.text, ''); assert.equal(result.code, null);
  assert.equal(result.usageKnown, true); assert.equal(tools, 1); assert.equal(instances[1].results.length, 1);
  assert.equal(instances[1].requests.filter(request => request.method === 'turn/start').length, 1);
  assert.ok(instances.every(rpc => rpc.closed));
});

test('nepomestivshiysya pervyy kontekst ostayotsya failed bez modelnogo zaprosa', async () => {
  const instances = [];
  const provider = new CodexProvider({ cwd: '/empty', startRpc: () => { const rpc = new FakeRpc(); instances.push(rpc); return rpc; } });
  const result = await provider.run({ ...job, maxInputTokens: 1 }, { signal: new AbortController().signal, emit() {}, tool() { assert.fail('No tools'); } });
  assert.equal(result.status, 'failed'); assert.equal(result.code, 'context_limit');
  assert.ok(instances.every(rpc => rpc.requests.every(request => request.method !== 'turn/start')));
});

test('povtory usage snapshot i nachalnyy nol ne rashoduyut tri modelnyh shaga', async () => {
  class UsageRpc extends FakeRpc {
    usage(n) {
      const value = { total: { inputTokens: n * 100, outputTokens: n * 10 }, last: { inputTokens: n ? 100 : 0, outputTokens: n ? 10 : 0 } };
      this.event('thread/tokenUsage/updated', { tokenUsage: value });
      this.event('thread/tokenUsage/updated', { tokenUsage: value });
    }
    async request(method, params) {
      if (method !== 'turn/start') return super.request(method, params);
      this.requests.push({ method, params });
      queueMicrotask(() => {
        this.usage(0); this.usage(1);
        this.event('item/tool/call', { callId: 'read1', tool: 'gh_farm_report', arguments: {} }, 40);
      });
      return { turn: { id: 'turn1' } };
    }
    respond(id, result) {
      this.results.push({ id, result });
      if (id === 40) {
        this.usage(2);
        this.event('item/tool/call', { callId: 'read2', tool: 'gh_farm_report', arguments: {} }, 41);
      } else {
        this.event('item/agentMessage/delta', { delta: 'План проверен' });
        this.usage(3);
        this.event('turn/completed', { turn: { status: 'completed' } });
      }
    }
  }
  const instances = []; let tools = 0;
  const provider = new CodexProvider({ cwd: '/empty', startRpc: () => { const rpc = new UsageRpc(); instances.push(rpc); return rpc; } });
  const outcome = await provider.run(job, { signal: new AbortController().signal, emit() {}, tool: async () => { tools++; return { ok: true }; } });
  assert.equal(outcome.status, 'completed'); assert.equal(outcome.text, 'План проверен');
  assert.equal(tools, 2); assert.equal(outcome.inputTokens, 300); assert.equal(outcome.outputTokens, 30);
  assert.equal(instances[1].results.length, 2); assert.ok(instances.every(rpc => rpc.closed));
});

test('chetvertyy realnyy usage rost po-prezhnemu ostanavlivaet otvet', async () => {
  class FourCallsRpc extends FakeRpc {
    async request(method, params) {
      if (method !== 'turn/start') return super.request(method, params);
      this.requests.push({ method, params });
      queueMicrotask(() => {
        this.event('item/agentMessage/delta', { delta: 'Частичный ответ' });
        for (let n = 1; n <= 4; n++) this.event('thread/tokenUsage/updated', { tokenUsage: { total: { inputTokens: n * 100, outputTokens: n * 10 }, last: { inputTokens: 100, outputTokens: 10 } } });
        this.event('turn/completed', { turn: { status: 'completed' } });
      });
      return { turn: { id: 'turn1' } };
    }
  }
  const provider = new CodexProvider({ cwd: '/empty', startRpc: () => new FourCallsRpc() });
  const outcome = await provider.run(job, { signal: new AbortController().signal, emit() {}, tool() { assert.fail('No tools expected'); } });
  assert.equal(outcome.status, 'incomplete'); assert.equal(outcome.text, 'Частичный ответ');
  assert.equal(outcome.inputTokens, 400); assert.equal(outcome.outputTokens, 40);
});
