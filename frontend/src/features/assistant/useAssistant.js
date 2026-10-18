import { useCallback, useEffect, useRef, useState } from 'react';
import { assistantRequest, uploadAssistantPhoto, watchAssistant } from '../../api/assistant';

export default function useAssistant(initialText = '') {
  const [capabilities, setCapabilities] = useState(null); const [chats, setChats] = useState([]);
  const [chatId, setChatId] = useState(null); const [messages, setMessages] = useState([]);
  const [text, setText] = useState(initialText); const [files, setFiles] = useState([]);
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [retryAt, setRetryAt] = useState(0); const [now, setNow] = useState(Date.now());
  const [options, setOptions] = useState({}); const [pending, setPending] = useState(null);
  const live = useRef(true); const sending = useRef(false);
  const active = messages.find(m => ['queued', 'running'].includes(m.status));
  const fail = useCallback(e => { if (live.current && e.name !== 'AbortError') { setError(e.message); if (e.retryAfter) setRetryAt(Date.now() + e.retryAfter * 1000); } }, []);
  useEffect(() => {
    live.current = true;
    (async () => {
      let cap = await assistantRequest('/capabilities');
      if (cap.owner) { const models = await assistantRequest('/models'); cap = { ...await assistantRequest('/capabilities'), models }; }
      const conversations = await assistantRequest('/conversations');
      if (!live.current) return;
      const selected = conversations.find(c => c.id === cap.preferences?.conversationId) || conversations[0];
      setCapabilities(cap); setChats(conversations); setOptions({ model: selected?.model || cap.model, effort: selected?.effort || cap.effort });
      setChatId(selected?.id || null);
    })().catch(fail);
    return () => { live.current = false; };
  }, [fail]);
  useEffect(() => {
    if (retryAt <= now) return undefined;
    const id = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(id);
  }, [retryAt, now]);
  const reload = useCallback(async () => {
    if (!chatId) return;
    const data = await assistantRequest(`/conversations/${chatId}/messages`);
    if (live.current) setMessages(data);
  }, [chatId]);
  useEffect(() => {
    let keep = true;
    if (!chatId) return undefined;
    const load = () => assistantRequest(`/conversations/${chatId}/messages`).then(data => { if (keep) setMessages(data); }).catch(fail);
    load(); const timer = setInterval(() => { if (document.visibilityState === 'visible') load(); }, 5000);
    return () => { keep = false; clearInterval(timer); };
  }, [chatId, fail]);
  useEffect(() => {
    if (!active?.id) return undefined;
    const abort = new AbortController();
    watchAssistant(active.id, next => setMessages(old => old.map(m => m.id === next.id ? next : m)), abort.signal).catch(() => { /* Obychnoe chtenie prodolzhaet obnovlyat otvet pri obryve potoka. */ });
    return () => abort.abort();
  }, [active?.id]);
  async function run(action) {
    if (sending.current) return;
    sending.current = true; setBusy(true); setError('');
    try { return await action(); } catch (e) { fail(e); } finally { sending.current = false; if (live.current) setBusy(false); }
  }
  const choose = async id => run(async () => {
    const chat = chats.find(c => c.id === id);
    setChatId(id); setMessages([]); setPending(null);
    if (chat) setOptions({ model: chat.model, effort: chat.effort });
  });
  const create = () => run(async () => {
    const chat = await assistantRequest('/conversations', 'POST', capabilities.owner ? options : {});
    setChats(old => [chat, ...old]); setChatId(chat.id); setMessages([]); setPending(null); return chat;
  });
  const configure = next => run(async () => {
    if (chatId) {
      const chat = await assistantRequest(`/conversations/${chatId}`, 'PUT', next);
      setChats(old => old.map(c => c.id === chatId ? chat : c));
    }
    setOptions(next);
  });
  const send = () => run(async () => {
    if (active || retryAt > Date.now()) return;
    let request = pending, id = chatId;
    if (!request) {
      if (!id) {
        const chat = await assistantRequest('/conversations', 'POST', capabilities.owner ? options : {});
        id = chat.id; setChatId(id); setChats(old => [chat, ...old]);
      }
      const attachments = [];
      for (const file of files) attachments.push((await uploadAssistantPhoto(id, file)).id);
      request = { clientKey: crypto.randomUUID(), text, attachments }; setPending(request);
    }
    const message = await assistantRequest(`/conversations/${id}/messages`, 'POST', request);
    if (!live.current) return;
    setMessages(old => [...old.filter(m => m.id !== message.id), message]); setPending(null); setText(''); setFiles([]);
    const [cap, conversations] = await Promise.all([assistantRequest('/capabilities'), assistantRequest('/conversations')]);
    if (live.current) { setCapabilities(cap); setChats(conversations); }
  });
  const proposal = (item, reject) => run(async () => {
    await assistantRequest(`/proposals/${item.id}/${reject ? 'reject' : 'confirm'}`, 'POST', { revision: item.revision }); await reload();
  });
  const cancel = () => run(async () => { if (active) await assistantRequest(`/messages/${active.id}`, 'DELETE'); await reload(); });
  const preferences = value => run(async () => {
    const result = await assistantRequest('/preferences', 'PUT', { ...value, conversationId: chatId });
    setCapabilities(old => ({ ...old, preferences: result }));
  });
  return { capabilities, chats, chatId, messages, text, setText, files, setFiles, busy, error, options, active, pending,
    retrySeconds: Math.max(0, Math.ceil((retryAt - now) / 1000)), choose, create, configure, send, proposal, cancel, preferences };
}
