import { spawn } from 'node:child_process';
import { functionTools, inputMessages, LIMITS, ProviderFailure } from './responses.mjs';

export class CodexRpc {
  constructor(binary, cwd) {
    this.pending = new Map(); this.nextId = 0; this.buffer = ''; this.listeners = new Set();
    this.child = spawn(binary, ['app-server', '--listen', 'stdio://'], {
      cwd, stdio: ['pipe', 'pipe', 'ignore'], detached: process.platform !== 'win32',
      env: { ...process.env, OPENAI_API_KEY: '', OPENAI_PROJECT: '', DEEPSEEK_API_KEY: '' },
    });
    this.child.stdout.setEncoding('utf8');
    this.child.stdout.on('data', chunk => {
      this.buffer += chunk;
      if (this.buffer.length > 4 * 1024 * 1024) return this.close();
      let end;
      while ((end = this.buffer.indexOf('\n')) >= 0) {
        const line = this.buffer.slice(0, end); this.buffer = this.buffer.slice(end + 1);
        if (!line.trim()) continue;
        let message;
        try { message = JSON.parse(line); } catch { this.close(); return; }
        if (message.id !== undefined && !message.method) {
          const pending = this.pending.get(message.id); this.pending.delete(message.id);
          if (message.error) pending?.reject(new ProviderFailure('codex_rpc_error'));
          else pending?.resolve(message.result);
        } else for (const listener of this.listeners) listener(message);
      }
    });
    const failed = () => {
      for (const pending of this.pending.values()) pending.reject(new ProviderFailure('codex_disconnected'));
      this.pending.clear(); for (const listener of this.listeners) listener({ method: 'disconnected' });
    };
    this.child.on('error', failed); this.child.on('exit', failed);
  }
  write(value) { if (!this.child.stdin.destroyed) this.child.stdin.write(JSON.stringify(value) + '\n'); }
  request(method, params) {
    const id = ++this.nextId;
    return new Promise((resolve, reject) => { this.pending.set(id, { resolve, reject }); this.write({ id, method, params }); });
  }
  notify(method, params = {}) { this.write({ method, params }); }
  respond(id, result) { this.write({ id, result }); }
  reject(id) { this.write({ id, error: { code: -32601, message: 'Operation unavailable' } }); }
  on(listener) { this.listeners.add(listener); return () => this.listeners.delete(listener); }
  close() {
    if (this.closed) return; this.closed = true;
    const kill = signal => {
      try { if (process.platform === 'win32') this.child.kill(signal); else process.kill(-this.child.pid, signal); } catch {}
    };
    kill('SIGTERM'); setTimeout(() => kill('SIGKILL'), 2000).unref();
  }
}

export class CodexProvider {
  constructor({ binary, cwd, startRpc = () => new CodexRpc(binary, cwd) }) {
    this.cwd = cwd; this.startRpc = startRpc; this.catalog = null; this.catalogAt = 0;
  }
  async connect(signal) {
    const rpc = this.startRpc();
    const stop = () => rpc.close(); signal.addEventListener('abort', stop, { once: true });
    try {
      signal.throwIfAborted();
      await rpc.request('initialize', { clientInfo: { name: 'growerhub', title: 'GrowerHub', version: '1.0.0' }, capabilities: { experimentalApi: true } });
      rpc.notify('initialized');
      return { rpc, close: () => { signal.removeEventListener('abort', stop); rpc.close(); } };
    } catch (error) { signal.removeEventListener('abort', stop); rpc.close(); throw error; }
  }
  async models() {
    if (this.catalog && Date.now() - this.catalogAt < 300000) return this.catalog;
    const connection = await this.connect(AbortSignal.timeout(25000));
    try {
      const models = []; let cursor = null;
      for (let page = 0; page < 5; page++) {
        const result = await connection.rpc.request('model/list', { cursor, limit: 100, includeHidden: false });
        models.push(...result.data.filter(item => !item.hidden).map(item => ({
          id: item.model, name: item.displayName, efforts: item.supportedReasoningEfforts.map(e => e.reasoningEffort),
          defaultEffort: item.defaultReasoningEffort, images: item.inputModalities.includes('image'), preferred: item.isDefault,
        })));
        cursor = result.nextCursor; if (!cursor) break;
      }
      if (!models.length) throw new ProviderFailure('models_unavailable');
      this.catalog = models; this.catalogAt = Date.now(); return models;
    } finally { connection.close(); }
  }
  async run(job, { signal, emit, tool }) {
    let connection, text = '', inputTokens = 0, outputTokens = 0, calls = 0, toolCount = 0, pendingTools = 0, limited = false;
    try {
      const model = (await this.models()).find(item => item.id === job.model);
      if (!model || !model.efforts.includes(job.effort)) throw new ProviderFailure('model_rejected');
      const messages = inputMessages(job.messages), tools = functionTools(job.tools);
      const allowed = new Set(tools.map(t => t.name));
      const instructions = messages.filter(m => m.role === 'system').map(m => m.content).join('\n');
      const history = messages.filter(m => m.role !== 'system');
      const input = [], transcript = [];
      let conservativeTokens = Buffer.byteLength(instructions + JSON.stringify(tools), 'utf8');
      for (const message of history) {
        const parts = typeof message.content === 'string' ? [{ type: 'input_text', text: message.content }] : message.content;
        transcript.push(message.role === 'user' ? 'Пользователь:' : 'Помощник:');
        for (const part of parts) {
          if (part.type === 'input_text') { transcript.push(part.text); conservativeTokens += Buffer.byteLength(part.text, 'utf8'); }
          else {
            if (!model.images) throw new ProviderFailure('images_unavailable');
            input.push({ type: 'image', url: part.image_url }); conservativeTokens += 6144;
          }
        }
      }
      // Dlya lichnogo Codex do zapuska ispolzuetsya konservativnaya verhnyaya ocenka teksta.
      if (conservativeTokens > Math.min(job.maxInputTokens, LIMITS.input)) throw new ProviderFailure('context_limit');
      input.unshift({ type: 'text', text: transcript.join('\n\n') });
      connection = await this.connect(signal); const { rpc } = connection;
      const started = await rpc.request('thread/start', {
        model: job.model, allowProviderModelFallback: false, cwd: this.cwd,
        approvalPolicy: 'never', sandbox: 'read-only', ephemeral: true, environments: [],
        baseInstructions: instructions, developerInstructions: 'Доступны только предметные инструменты GrowerHub. Не используй файлы, shell, интернет, другие приложения и внутренние инструменты Codex.',
        dynamicTools: tools.map(t => ({ type: 'function', name: t.name, description: t.description, inputSchema: t.parameters })),
      });
      const threadId = started.thread.id;
      const completed = new Promise((resolve, reject) => {
        rpc.on(message => {
          const p = message.params;
          if (message.method === 'disconnected') return reject(new ProviderFailure('codex_disconnected'));
          if (message.id !== undefined) {
            if (message.method !== 'item/tool/call' || p.threadId !== threadId || !allowed.has(p.tool)
                || ++toolCount > Math.min(job.maxToolCalls, LIMITS.tools) || calls > Math.min(job.maxModelCalls, LIMITS.calls)) {
              rpc.reject(message.id); reject(new ProviderFailure('tool_rejected')); return;
            }
            pendingTools++;
            Promise.resolve(tool({ id: p.callId, name: p.tool, arguments: p.arguments })).then(result => {
              pendingTools--;
              // Kartochka uzhe podgotovlena backend; ne zapuskaem novuju generaciyu s ee rezultatom.
              if (calls >= Math.min(job.maxModelCalls, LIMITS.calls)) {
                limited = true; if (!pendingTools) resolve(); return;
              }
              rpc.respond(message.id, { contentItems: [{ type: 'inputText', text: JSON.stringify(result) }], success: true });
            }, () => {
              pendingTools--;
              if (calls >= Math.min(job.maxModelCalls, LIMITS.calls)) { limited = true; if (!pendingTools) resolve(); return; }
              rpc.respond(message.id, { contentItems: [{ type: 'inputText', text: '{"error":"tool_unavailable"}' }], success: false });
            });
            return;
          }
          if (p?.threadId !== threadId) return;
          if (message.method === 'item/agentMessage/delta') {
            text += p.delta;
            if (text.length > LIMITS.text) return reject(new ProviderFailure('output_limit'));
            emit({ type: 'delta', text: p.delta });
          }
          if (message.method === 'thread/tokenUsage/updated') {
            // Povtornyy snapshot i nachalnyy nol ne yavlyayutsya novoy generaciey.
            const nextInput = Math.max(inputTokens, p.tokenUsage.total.inputTokens);
            const nextOutput = Math.max(outputTokens, p.tokenUsage.total.outputTokens);
            if (nextInput > inputTokens || nextOutput > outputTokens) calls++;
            inputTokens = nextInput; outputTokens = nextOutput;
            if (p.tokenUsage.last.inputTokens > Math.min(job.maxInputTokens, LIMITS.input)
                || p.tokenUsage.last.outputTokens > Math.min(job.maxOutputTokens, LIMITS.output)
                || calls > Math.min(job.maxModelCalls, LIMITS.calls)) reject(new ProviderFailure('context_limit'));
          }
          if (message.method === 'turn/completed') {
            if (p.turn.status === 'completed') resolve(); else reject(new ProviderFailure('codex_turn_failed'));
          }
        });
      });
      // Podpiska ustanovlena do turn/start: bystryj otvet ne mozhet poteryatsya.
      const turn = rpc.request('turn/start', { threadId, input, model: job.model, effort: job.effort,
        environments: [], approvalPolicy: 'never', serviceTierForTurn: 'default' });
      await Promise.all([turn, completed]);
      return { status: limited ? 'incomplete' : 'completed', text, inputTokens, outputTokens, usageKnown: true, code: null };
    } catch (error) {
      if (!signal.aborted && error.code === 'context_limit' && (text || calls > 0 || toolCount > 0))
        return { status: 'incomplete', text, inputTokens, outputTokens, usageKnown: true, code: null };
      return { status: signal.aborted ? 'cancelled' : 'failed', text, inputTokens, outputTokens,
        usageKnown: false, code: error instanceof ProviderFailure ? error.code : 'codex_unavailable' };
    } finally { connection?.close(); }
  }
}
