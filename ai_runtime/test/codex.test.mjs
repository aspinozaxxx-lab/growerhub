import test from 'node:test';
import assert from 'node:assert/strict';
import { CodexProvider } from '../src/codex.mjs';

class FakeRpc {
  constructor(mode) { this.mode = mode; this.requests = []; this.listeners = []; this.results = []; }
  on(listener) { this.listeners.push(listener); }
  notify() {}
  close() { this.closed = true; }
  reject(id) { this.rejected = id; }
  respond(id, result) { this.results.push({ id, result }); this.event('turn/completed', { turn: { status: 'completed' } }); }
  event(method, params, id) { this.listeners.forEach(l => l({ method, params: { threadId: 't1', ...params }, ...(id === undefined ? {} : { id }) })); }
  async request(method, params) {
    this.requests.push({ method, params });
    if (method === 'initialize') return {};
    if (method === 'model/list') return { data: [{ model: 'test-model', displayName: 'Test', supportedReasoningEfforts: [{ reasoningEffort: 'medium' }],
      defaultReasoningEffort: 'medium', inputModalities: ['text', 'image'], isDefault: true }] };
    if (method === 'thread/start') return { thread: { id: 't1' } };
    if (method === 'turn/start') {
      queueMicrotask(() => {
        this.event('item/agentMessage/delta', { delta: 'Готово' });
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
test('Unexpected Codex RPC never reaches a backend tool', async () => {
  const instances = []; const provider = new CodexProvider({ cwd: '/empty', startRpc: () => { const rpc = new FakeRpc('bad'); instances.push(rpc); return rpc; } });
  const outcome = await provider.run(job, { signal: new AbortController().signal, emit() {}, tool() { assert.fail('must not run'); } });
  assert.equal(outcome.code, 'tool_rejected'); assert.equal(instances[1].rejected, 40); assert.ok(instances[1].closed);
});
