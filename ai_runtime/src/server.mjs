import { runtimeServer } from './http.mjs';
import { ResponsesProvider } from './responses.mjs';
import { CodexProvider } from './codex.mjs';

const mode = process.env.GH_AI_MODE;
if (!['owner', 'public'].includes(mode)) throw new Error('GH_AI_MODE required');
const owner = mode === 'owner';
const provider = owner ? new CodexProvider({ binary: process.env.GH_CODEX_BINARY, cwd: process.env.GH_CODEX_WORKSPACE })
  : new ResponsesProvider({ apiKey: process.env.DEEPSEEK_API_KEY });
const server = runtimeServer({ provider, token: process.env.GH_AI_TOKEN,
  enabled: owner || process.env.GH_AI_PUBLIC_ENABLED === 'true',
  concurrency: owner ? 1 : 2, timeoutMs: owner ? 540000 : 150000 });
server.listen(owner ? 8787 : 8788, '127.0.0.1');
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => {
  server.close(); setTimeout(() => process.exit(0), 5000).unref();
});
