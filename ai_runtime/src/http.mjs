import { createServer } from 'node:http';
import { timingSafeEqual } from 'node:crypto';

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

async function jsonBody(request, maximum) {
  let size = 0; const chunks = [];
  for await (const chunk of request) {
    size += chunk.length;
    if (size > maximum) throw new Error('body_limit');
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

export function runtimeServer({ provider, token, concurrency, timeoutMs, enabled = true, maxBodyBytes = 20 * 1024 * 1024 }) {
  if (!token || token.length < 32) throw new Error('Service token is required');
  const expected = Buffer.from(`Bearer ${token}`), active = new Map(), seen = new Map();
  const respond = (response, status, value) => {
    response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    response.end(JSON.stringify(value));
  };
  return createServer({ requestTimeout: timeoutMs, headersTimeout: Math.min(timeoutMs, 15000) }, async (request, response) => {
    const actual = Buffer.from(request.headers.authorization || '');
    if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) return respond(response, 401, { error: 'unauthorized' });
    try {
      const url = new URL(request.url, 'http://127.0.0.1');
      if (url.search) return respond(response, 400, { error: 'query_not_supported' });
      if (request.method === 'GET' && url.pathname === '/v1/health') return respond(response, 200, { ready: true, generationEnabled: enabled, active: active.size });
      if (request.method === 'GET' && url.pathname === '/v1/models') return respond(response, 200, { models: await provider.models() });
      const match = url.pathname.match(/^\/v1\/jobs\/([^/]+)(?:\/tools\/([^/]+))?$/);
      if (match && request.method === 'DELETE' && !match[2]) {
        active.get(match[1])?.abort.abort(); return respond(response, 200, { cancelled: true });
      }
      if (match && request.method === 'POST' && match[2]) {
        const context = active.get(match[1]), pending = context?.pending.get(decodeURIComponent(match[2]));
        if (!pending) return respond(response, 409, { error: 'tool_not_pending' });
        const result = await jsonBody(request, 100000);
        context.pending.delete(decodeURIComponent(match[2])); pending.resolve(result);
        return respond(response, 200, { accepted: true });
      }
      if (request.method !== 'POST' || url.pathname !== '/v1/jobs') return respond(response, 404, { error: 'not_found' });
      if (!enabled) return respond(response, 503, { error: 'generation_disabled' });
      if (active.size >= concurrency) return respond(response, 429, { error: 'busy' });
      const job = await jsonBody(request, maxBodyBytes);
      if (!uuid.test(job.id) || !Array.isArray(job.messages) || !Array.isArray(job.tools)
          || ![job.maxInputTokens, job.maxOutputTokens, job.maxModelCalls, job.maxToolCalls].every(v => Number.isSafeInteger(v) && v > 0))
        return respond(response, 400, { error: 'invalid_job' });
      // Povtornyj POST ne zapuskaet model dazhe posle poteri otveta.
      const now = Date.now();
      for (const [id, time] of seen) if (time < now - 86400000) seen.delete(id);
      if (seen.has(job.id)) return respond(response, 409, { error: 'duplicate_job' });
      if (active.size >= concurrency) return respond(response, 429, { error: 'busy' });
      const abort = new AbortController(), pending = new Map();
      active.set(job.id, { abort, pending }); seen.set(job.id, now);
      const timer = setTimeout(() => abort.abort(), timeoutMs);
      response.writeHead(200, { 'Content-Type': 'application/x-ndjson', 'Cache-Control': 'no-store', 'X-Accel-Buffering': 'no' });
      const emit = event => { if (!response.destroyed) response.write(JSON.stringify(event) + '\n'); };
      response.on('close', () => abort.abort());
      abort.signal.addEventListener('abort', () => { for (const item of pending.values()) item.reject(new Error('cancelled')); pending.clear(); }, { once: true });
      emit({ type: 'started', id: job.id });
      try {
        const outcome = await provider.run(job, { signal: abort.signal, emit, tool: call => {
          abort.signal.throwIfAborted();
          if (typeof call.id !== 'string' || call.id.length > 200 || pending.has(call.id)) throw new Error('invalid_tool_id');
          return new Promise((resolve, reject) => {
            pending.set(call.id, { resolve, reject }); emit({ type: 'tool', ...call });
          });
        } });
        emit({ type: 'completed', ...outcome });
      } catch { emit({ type: 'completed', status: 'failed', text: '', inputTokens: 0, outputTokens: 0, usageKnown: false, code: 'runtime_failure' }); }
      finally { clearTimeout(timer); active.delete(job.id); abort.abort(); response.end(); }
    } catch {
      if (!response.headersSent) respond(response, 400, { error: 'invalid_request' });
      else response.end();
    }
  });
}
