export const PUBLIC_MODEL = 'deepseek-flash';
export const LIMITS = Object.freeze({ input: 16000, output: 4096, calls: 3, tools: 6, text: 24000 });
// Verhnij tarif bez skidok za kesh i vremya sutok, nanoUSD za token.
export const PRICE = Object.freeze({ input: 300, output: 1200, turnMicros: 15000 });

export function inputUpperBound(input, tools) {
  let images = 0, nodes = 0;
  const encoded = JSON.stringify({ input, tools }, (key, value) => {
    if (value && typeof value === 'object') nodes++;
    if (key === 'image_url') { images++; return '[image]'; }
    return value;
  });
  // Bajtovaya verhnyaya ocenka BPE, zapas na framing i 2048 vmesto maksimuma 1024 na foto.
  return Buffer.byteLength(encoded, 'utf8') + nodes * 32 + images * 2048 + 1024;
}

export class ProviderFailure extends Error {
  constructor(code, usageKnown = true) { super(code); this.code = code; this.usageKnown = usageKnown; }
}

export function inputMessages(messages) {
  return messages.map(message => {
    if (!['system', 'user', 'assistant'].includes(message.role)) throw new ProviderFailure('invalid_message');
    if (!Array.isArray(message.content)) throw new ProviderFailure('invalid_message');
    if (message.role !== 'user') {
      if (message.content.some(item => item.type !== 'text')) throw new ProviderFailure('invalid_message');
      return { role: message.role, content: message.content.map(item => item.text).join('\n') };
    }
    return { role: 'user', content: message.content.map(item => {
      if (item.type === 'text' && typeof item.text === 'string') return { type: 'input_text', text: item.text };
      if (item.type === 'image' && item.mime === 'image/jpeg' && typeof item.data === 'string'
          && item.data.length <= 8 * 1024 * 1024 && /^[A-Za-z0-9+/]*={0,2}$/.test(item.data))
        return { type: 'input_image', image_url: `data:image/jpeg;base64,${item.data}`, detail: 'auto' };
      throw new ProviderFailure('invalid_image');
    }) };
  });
}

export function functionTools(tools) {
  if (!Array.isArray(tools) || tools.length > 20) throw new ProviderFailure('invalid_tools');
  return tools.map(tool => {
    if (tool.type !== 'function' || typeof tool.name !== 'string' || !/^gh_[a-z_]+$/.test(tool.name)
        || !tool.parameters || typeof tool.description !== 'string') throw new ProviderFailure('invalid_tools');
    return { type: 'function', name: tool.name, description: tool.description, parameters: tool.parameters, strict: false };
  });
}

async function readResponse(response, emit, signal) {
  if (!response.ok) throw new ProviderFailure(`provider_http_${response.status}`, response.status < 500);
  const decoder = new TextDecoder();
  let buffer = '', final;
  for await (const chunk of response.body) {
    signal.throwIfAborted();
    buffer += decoder.decode(chunk, { stream: true });
    if (buffer.length > 2 * 1024 * 1024) throw new ProviderFailure('provider_frame_too_large', false);
    let end;
    while ((end = buffer.search(/\r?\n\r?\n/)) >= 0) {
      const separator = buffer.slice(end).match(/^\r?\n\r?\n/)[0];
      const frame = buffer.slice(0, end); buffer = buffer.slice(end + separator.length);
      const data = frame.split('\n').filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n');
      if (!data || data === '[DONE]') continue;
      let event;
      try { event = JSON.parse(data); } catch { throw new ProviderFailure('provider_invalid_frame', false); }
      if (event.type === 'response.output_text.delta') emit({ type: 'delta', text: event.delta });
      if (['response.completed', 'response.incomplete', 'response.failed'].includes(event.type)) final = event.response;
      if (event.type === 'error') throw new ProviderFailure('provider_stream_error', false);
    }
  }
  if (!final || !final.usage) throw new ProviderFailure('provider_result_unknown', false);
  return final;
}

export class ResponsesProvider {
  constructor({ apiKey, transport = fetch, apiBase = 'https://api.deepseek.com' }) {
    this.apiKey = apiKey; this.transport = transport; this.apiBase = apiBase;
  }

  async models() {
    if (!this.apiKey) throw new ProviderFailure('api_not_configured');
    const response = await this.transport(`${this.apiBase}/models`, {
      headers: this.headers(), signal: AbortSignal.timeout(15000), redirect: 'error',
    });
    if (!response.ok) throw new ProviderFailure(`provider_http_${response.status}`);
    const model = (await response.json()).data?.find(item => item.id === PUBLIC_MODEL);
    if (!model?.input_modalities?.includes('image') || !model.effort?.supported_levels?.includes('high'))
      throw new ProviderFailure('model_unavailable');
    return [{ id: PUBLIC_MODEL, name: 'DeepSeek Flash', efforts: ['high'], defaultEffort: 'high', images: true, preferred: true }];
  }

  headers() {
    return { Authorization: `Bearer ${this.apiKey}`, 'Content-Type': 'application/json' };
  }

  async run(job, { signal, emit, tool }) {
    let inputTokens = 0, outputTokens = 0, text = '', callCount = 0, toolCount = 0, outstanding = false;
    try {
      if (!this.apiKey) throw new ProviderFailure('api_not_configured');
      if (job.model !== PUBLIC_MODEL || job.effort !== 'high') throw new ProviderFailure('model_rejected');
      if (!Number.isSafeInteger(job.maxCostMicros) || job.maxCostMicros <= 0) throw new ProviderFailure('invalid_budget');
      const maximumCost = Math.min(job.maxCostMicros, PRICE.turnMicros) * 1000;
      const input = inputMessages(job.messages), tools = functionTools(job.tools);
      const allowed = new Set(tools.map(item => item.name));
      while (callCount < Math.min(job.maxModelCalls, LIMITS.calls)) {
        signal.throwIfAborted();
        const count = inputUpperBound(input, tools);
        if (count > Math.min(job.maxInputTokens, LIMITS.input)) throw new ProviderFailure('context_limit');
        const available = maximumCost - inputTokens * PRICE.input - outputTokens * PRICE.output - count * PRICE.input;
        const outputLimit = Math.min(job.maxOutputTokens, LIMITS.output, Math.floor(available / PRICE.output));
        if (outputLimit < 128) throw new ProviderFailure('turn_budget');
        const body = { model: PUBLIC_MODEL, input, tools,
          reasoning: { effort: 'high' }, store: false,
          max_output_tokens: outputLimit,
          tool_choice: callCount + 1 >= Math.min(job.maxModelCalls, LIMITS.calls) || toolCount >= Math.min(job.maxToolCalls, LIMITS.tools) ? 'none' : 'auto' };
        outstanding = true; callCount++;
        const response = await this.transport(`${this.apiBase}/responses`, {
          method: 'POST', headers: this.headers(), body: JSON.stringify({ ...body, stream: true }), signal, redirect: 'error',
        });
        if (!response.ok && response.status < 500) outstanding = false;
        const beforeText = text.length;
        const result = await readResponse(response, event => {
          if (event.type === 'delta') {
            text += event.text;
            if (text.length > LIMITS.text) throw new ProviderFailure('output_limit', false);
          }
          emit(event);
        }, signal);
        if (!Number.isSafeInteger(result.usage.input_tokens) || !Number.isSafeInteger(result.usage.output_tokens)
            || result.usage.input_tokens < 0 || result.usage.output_tokens < 0) throw new ProviderFailure('invalid_usage', false);
        inputTokens += result.usage.input_tokens; outputTokens += result.usage.output_tokens; outstanding = false;
        if (result.usage.input_tokens > count || result.usage.output_tokens > outputLimit
            || inputTokens * PRICE.input + outputTokens * PRICE.output > maximumCost)
          throw new ProviderFailure('provider_usage_overflow', false);
        if (result.status === 'failed') throw new ProviderFailure('provider_failed');
        if (text.length === beforeText) {
          const fallback = (result.output || []).filter(item => item.type === 'message').flatMap(item => item.content || [])
            .filter(item => item.type === 'output_text').map(item => item.text).join('');
          if (text.length + fallback.length > LIMITS.text) throw new ProviderFailure('output_limit');
          if (fallback) { text += fallback; emit({ type: 'delta', text: fallback }); }
        }
        const calls = (result.output || []).filter(item => item.type === 'function_call');
        if (!calls.length) return { status: result.status === 'incomplete' ? 'incomplete' : 'completed', text, inputTokens, outputTokens, usageKnown: true, code: null };
        if (body.tool_choice === 'none') throw new ProviderFailure('tool_limit');
        input.push(...result.output);
        for (const call of calls) {
          if (++toolCount > Math.min(job.maxToolCalls, LIMITS.tools)) throw new ProviderFailure('tool_limit');
          if (!allowed.has(call.name)) throw new ProviderFailure('tool_rejected');
          let argumentsValue;
          try { argumentsValue = JSON.parse(call.arguments); } catch { throw new ProviderFailure('invalid_tool_arguments'); }
          const answer = await tool({ id: call.call_id, name: call.name, arguments: argumentsValue });
          input.push({ type: 'function_call_output', call_id: call.call_id, output: JSON.stringify(answer) });
        }
      }
      throw new ProviderFailure('model_call_limit');
    } catch (error) {
      return { status: signal.aborted ? 'cancelled' : 'failed', text, inputTokens, outputTokens,
        usageKnown: !outstanding && error.usageKnown !== false, code: error instanceof ProviderFailure ? error.code : 'provider_unavailable' };
    }
  }
}
