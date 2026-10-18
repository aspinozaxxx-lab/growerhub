import test from 'node:test';
import assert from 'node:assert/strict';
import { ResponsesProvider, functionTools, inputMessages, inputUpperBound, LIMITS, PRICE } from '../src/responses.mjs';

const job = () => ({ model: 'deepseek-flash', effort: 'high', maxCostMicros: 15000, maxInputTokens: 16000, maxOutputTokens: 4096,
  maxModelCalls: 3, maxToolCalls: 6, messages: [{ role: 'user', content: [{ type: 'text', text: 'РљР°Рє СЂР°СЃС‚РµРЅРёСЏ?' }] }],
  tools: [{ type: 'function', name: 'gh_farm_report', description: 'Read own farm', parameters: { type: 'object', properties: {} } }] });
const completed = (output, input = 120, tokens = 40) => new Response(
  `data: ${JSON.stringify({ type: 'response.completed', response: { status: 'completed', usage: { input_tokens: input, output_tokens: tokens }, output } })}\n\n`,
  { headers: { 'Content-Type': 'text/event-stream' } });
const context = (overrides = {}) => ({ signal: new AbortController().signal, emit() {}, tool() { throw new Error('Unexpected tool'); }, ...overrides });

test('rejects model overrides, hosted tools and remote photos without a request', async () => {
  let calls = 0;
  const provider = new ResponsesProvider({ apiKey: 'test', transport: async () => { calls++; } });
  assert.equal((await provider.run({ ...job(), model: 'expensive' }, context())).code, 'model_rejected');
  assert.throws(() => functionTools([{ type: 'web_search' }]));
  assert.throws(() => inputMessages([{ role: 'user', content: [{ type: 'image', data: 'https://private/' }] }]));
  assert.equal(calls, 0);
});

test('counts complete input before every charged call, preserves reasoning and disables final tools', async () => {
  const sent = []; let generations = 0;
  const provider = new ResponsesProvider({ apiKey: 'test', transport: async (url, request) => {
    const body = JSON.parse(request.body); sent.push({ url, body });
    generations++;
    return completed(generations < 3 ? [{ type: 'reasoning', id: `r${generations}`, content: [{ type: 'reasoning_text', text: 'internal' }] },
      { type: 'function_call', call_id: `call${generations}`, name: 'gh_farm_report', arguments: '{}' }] : []);
  } });
  const result = await provider.run(job(), context({ tool: async () => ({ plants: 5 }) }));
  assert.equal(result.status, 'completed'); assert.equal(result.inputTokens, 360); assert.equal(result.outputTokens, 120);
  assert.equal(sent.length, 3); assert.equal(sent[2].body.tool_choice, 'none');
  assert.ok(sent[1].body.input.some(item => item.type === 'reasoning'));
  assert.ok(sent[1].body.input.some(item => item.type === 'function_call_output'));
  assert.equal(sent[0].body.store, false); assert.equal(sent[0].body.include, undefined);
  assert.equal(sent[1].body.max_output_tokens, LIMITS.output);
});

test('complete input including tool answers and photos is limited before generation', async () => {
  let requests = 0;
  const provider = new ResponsesProvider({ apiKey: 'test', transport: async () => { requests++; } });
  const huge = job(); huge.messages[0].content[0].text = 'я'.repeat(16000);
  assert.equal((await provider.run(huge, context())).code, 'context_limit');
  assert.equal(requests, 0);
  const a = inputUpperBound(inputMessages(job().messages), []);
  const b = inputUpperBound(inputMessages([{role:'user',content:[{type:'image',mime:'image/jpeg',data:'AAAA'}]}]), []);
  assert.ok(b - a > 2000);
});

test('each call reduces its output limit to the remaining turn budget', async () => {
  let generations = 0; const limits = [];
  const provider = new ResponsesProvider({ apiKey: 'test', transport: async (url, request) => {
    assert.ok(url.endsWith('/responses'));
    const body = JSON.parse(request.body); limits.push(body.max_output_tokens); generations++;
    return completed(generations < 3 ? [{type:'function_call',call_id:`c${generations}`,name:'gh_farm_report',arguments:'{}'}] : [], 600, body.max_output_tokens);
  }});
  const result = await provider.run({...job(), maxCostMicros:8000}, context({tool:async()=>({plants:2})}));
  assert.ok(limits[1] < limits[0]);
  assert.ok(result.inputTokens * PRICE.input + result.outputTokens * PRICE.output <= 8000000);
  assert.ok(result.status === 'completed' || result.code === 'turn_budget');
  assert.ok(generations >= 2 && generations <= LIMITS.calls);
});

test('uses measured prefix tokens without dropping farm facts or reasoning', async () => {
  const sample = job(); sample.messages[0].content[0].text = 'x'.repeat(11000);
  let generations = 0;
  const provider = new ResponsesProvider({ apiKey:'test', transport:async (_url, request) => {
    const body = JSON.parse(request.body); generations++;
    if (generations === 1) return completed([
      {type:'reasoning',content:[{type:'reasoning_text',text:'r'.repeat(2000)}]},
      {type:'function_call',call_id:'c1',name:'gh_farm_report',arguments:'{}'}], 2000, 1000);
    assert.ok(inputUpperBound(body.input, body.tools) > LIMITS.input);
    assert.ok(body.input.some(item => item.type === 'reasoning'));
    assert.ok(body.input.some(item => item.type === 'function_call_output' && item.output.includes('z'.repeat(6000))));
    return completed([], 6000, 50);
  }});
  const result = await provider.run(sample, context({tool:async()=>({facts:'z'.repeat(6000)})}));
  assert.equal(result.status, 'completed'); assert.equal(generations, 2);
});

test('measured prefix never exempts new oversized tool results from the input limit', async () => {
  let generations = 0;
  const provider = new ResponsesProvider({ apiKey:'test', transport:async () => {
    generations++; return completed([{type:'function_call',call_id:'c1',name:'gh_farm_report',arguments:'{}'}]);
  }});
  const result = await provider.run(job(), context({tool:async()=>({facts:'z'.repeat(16000)})}));
  assert.equal(result.code, 'context_limit'); assert.equal(generations, 1); assert.equal(result.usageKnown, true);
});

test('network failure after dispatch keeps conservative accounting; refusal before generation does not', async () => {
  for (const failure of [new Error('reset'), new Response('', { status: 503 }), new Response('', { status: 429 })]) {
    const provider = new ResponsesProvider({ apiKey: 'test', transport: async url => {
      if (failure instanceof Error) throw failure;
      return failure;
    } });
    const result = await provider.run(job(), context());
    assert.equal(result.status, 'failed'); assert.equal(result.usageKnown, failure.status === 429);
  }
});
