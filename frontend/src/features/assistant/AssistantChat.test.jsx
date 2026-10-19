import { beforeEach, afterEach, expect, test, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AssistantChat from './AssistantChat';
import { assistantRequest } from '../../api/assistant';
import { demoPrompts } from './demoPrompts';
vi.mock('../../api/assistant', () => ({ assistantRequest: vi.fn(), watchAssistant: vi.fn().mockResolvedValue(), uploadAssistantPhoto: vi.fn(), assistantPhoto: vi.fn() }));
vi.mock('../../locales/i18n', () => ({ getCurrentLocale: () => 'ru', translateApp: (value, params = {}) => value.replace(/\{\{(\w+)\}\}/g, (_, key) => String(params[key])) }));
const cap = { enabled: true, available: true, owner: false, demo: false, model: 'deepseek-flash', effort: 'high', models: [], limits: { remainingDay: 30 }, preferences: { telegram: false, report: 'off', hour: 9, weekday: 1 } };
const chat = { id: 'chat-1', title: 'Уход', model: 'deepseek-flash', effort: 'high' };
let history;
beforeEach(() => {
  vi.clearAllMocks(); history = [];
  assistantRequest.mockImplementation(async (path, method) => {
    if (path === '/capabilities') return cap;
    if (path === '/conversations') return [chat];
    if (path.endsWith('/messages') && !method) return history;
    return {};
  });
});
afterEach(cleanup);
const mount = () => render(<MemoryRouter><AssistantChat /></MemoryRouter>);
test('demo examples fill editable text without sending or applying changes', async () => {
  const original = assistantRequest.getMockImplementation();
  assistantRequest.mockImplementation((path, ...args) => path === '/capabilities' ? Promise.resolve({ ...cap, demo: true }) : original(path, ...args));
  mount();
  fireEvent.click(await screen.findByRole('button', { name: /Мой утренний обход/ }));
  expect(screen.getByRole('textbox', { name: 'Ваш вопрос' })).toHaveValue(demoPrompts[2].text);
  expect(screen.getByRole('textbox', { name: 'Ваш вопрос' })).toHaveFocus();
  expect(assistantRequest.mock.calls.some(([, method]) => method === 'POST')).toBe(false);
  expect(screen.queryByLabelText('Модель')).not.toBeInTheDocument();
});
test('owner model controls use the current catalog and preserve a supported effort', async () => {
  const models = [
    {id:'sol',name:'Sol',efforts:['low','max','ultra'],defaultEffort:'low'},
    {id:'astra',name:'Astra',efforts:['medium','max'],defaultEffort:'medium'},
  ];
  assistantRequest.mockImplementation(async(path,method,body)=> {
    if(path==='/capabilities') return {...cap,owner:true,model:'sol',models};
    if(path==='/models') return models;
    if(path==='/conversations') return [{...chat,model:'sol',effort:'max'}];
    if(path==='/conversations/chat-1' && method==='PUT') return {...chat,...body};
    if(path.endsWith('/messages')) return [];
    return {};
  });
  mount(); await screen.findByLabelText('Модель');
  expect(screen.getByLabelText('Усилие')).toHaveValue('max');
  fireEvent.change(screen.getByLabelText('Модель'),{target:{value:'astra'}});
  await waitFor(()=>expect(assistantRequest).toHaveBeenCalledWith('/conversations/chat-1','PUT',{model:'astra',effort:'max'}));
  await waitFor(()=>expect(screen.queryByRole('option',{name:'ultra'})).not.toBeInTheDocument());
  expect(screen.getByLabelText('Модель').closest('form')).toHaveClass('assistant-compose');
  fireEvent.click(screen.getByRole('button',{name:'Настройки помощника'}));
  fireEvent.click(screen.getByRole('button',{name:'Обновить список моделей'}));
  await waitFor(()=>expect(assistantRequest.mock.calls.filter(([p])=>p==='/models')).toHaveLength(2));
});
test('a proposal is never applied by reading the chat and confirmation carries its exact version', async () => {
  history = [{ id: 'message-1', text: 'Запиши уход', answer: 'Проверьте карточку', status: 'completed', attachments: [], proposals: [{ id: 'proposal-1', title: 'Записать осмотр', kind: 'care', payload: { text: 'Листья без пятен' }, status: 'pending', revision: 'version-7' }] }];
  mount(); await screen.findByText('Записать осмотр');
  expect(assistantRequest.mock.calls.some(([, method]) => method === 'POST')).toBe(false);
  fireEvent.click(screen.getByRole('button', { name: 'Подтвердить', exact: true }));
  await waitFor(() => expect(assistantRequest).toHaveBeenCalledWith('/proposals/proposal-1/confirm', 'POST', { revision: 'version-7' }));
  expect(screen.queryByLabelText('Модель')).not.toBeInTheDocument();
});
test('owner model choices recover after a server restart clears the catalog during an open chat', async () => {
  const models = [{ id: 'sol', name: 'Sol', efforts: ['low', 'max'], defaultEffort: 'low' }];
  assistantRequest.mockImplementation(async (path, method, body) => {
    if (path === '/capabilities') return { ...cap, owner: true, demo: true, model: null, models: [] };
    if (path === '/models') return models;
    if (path === '/conversations') return [{ ...chat, model: 'sol', effort: 'max' }];
    if (path.endsWith('/messages') && method === 'POST') return { id: 'sent', text: body.text, status: 'completed', answer: 'Готово', attachments: [], proposals: [] };
    if (path.endsWith('/messages')) return [];
    return {};
  });
  mount(); await screen.findByLabelText('Модель');
  fireEvent.change(screen.getByRole('textbox', { name: 'Ваш вопрос' }), { target: { value: 'Как растения?' } });
  fireEvent.click(screen.getByRole('button', { name: 'Отправить', exact: true }));
  await waitFor(() => expect(assistantRequest.mock.calls.filter(([p]) => p === '/models')).toHaveLength(2));
  await waitFor(() => expect(screen.getByLabelText('Модель')).toHaveValue('sol'));
  expect(screen.getByLabelText('Усилие')).toHaveValue('max');
  expect(screen.getByLabelText('Модель')).toBeEnabled();
});
test('an ambiguous send does not automatically retry and manual retry keeps the same request key', async () => {
  const ordinary = assistantRequest.getMockImplementation(); let failed = false;
  assistantRequest.mockImplementation(async (path, method, body) => {
    if (path.endsWith('/messages') && method === 'POST') {
      if (!failed) { failed = true; throw new Error('Связь прервалась'); }
      return { id: 'sent', text: body.text, status: 'completed', answer: 'Ответ', attachments: [], proposals: [] };
    }
    return ordinary(path, method, body);
  });
  mount(); await screen.findByRole('textbox', { name: 'Ваш вопрос' });
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Как растение?' } });
  fireEvent.click(screen.getByRole('button', { name: 'Отправить', exact: true }));
  await screen.findByText('Связь прервалась');
  const sent = () => assistantRequest.mock.calls.filter(([, method]) => method === 'POST');
  expect(sent()).toHaveLength(1);
  fireEvent.click(screen.getByRole('button', { name: 'Проверить отправку' }));
  await waitFor(() => expect(sent()).toHaveLength(2));
  expect(sent()[0][2]).toEqual(sent()[1][2]);
});
test('device delivery is shown separately from actual device state', async () => {
  history = [{ id: 'message-1', text: 'Включи', status: 'completed', attachments: [], proposals: [{ id: 'proposal-1', title: 'Лампа', kind: 'device', payload: { type: 'set', value: 'ON' }, status: 'applied', revision: 'version-7' }] }];
  mount(); await screen.findByText('Команда передана устройству. Фактическое состояние смотрите на ферме.');
  expect(screen.queryByRole('button', { name: 'Подтвердить' })).not.toBeInTheDocument();
});
test('a saved rule has a direct link to its own details in automations', async () => {
  history = [{ id: 'message-1', text: 'Сохрани сценарий', answer: '[Список сценариев](/app/automations/)', status: 'completed', attachments: [], proposals: [{ id: 'proposal-1', title: 'Утренний осмотр', kind: 'rule', payload: { enabled: false }, status: 'applied', revision: 'version-7', result: { id: 'owned-rule' } }] }];
  const close = vi.fn(); render(<MemoryRouter><AssistantChat onClose={close} /></MemoryRouter>);
  expect(await screen.findByRole('link', { name: 'Открыть сценарий' })).toHaveAttribute('href', '/app/automations/?rule=owned-rule');
  fireEvent.click(screen.getByRole('link', { name: 'Открыть сценарий' }));
  expect(close).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('link', { name: 'Список сценариев' }));
  expect(close).toHaveBeenCalledTimes(2);
  expect(assistantRequest.mock.calls.some(([, method]) => method === 'POST')).toBe(false);
});
test('feature request displays the complete text and waits for explicit sending', async () => {
  history = [{ id: 'message-1', text: 'Нужна доработка', status: 'completed', attachments: [], proposals: [{ id: 'request-1', title: 'Отправить команде: Удобрения', kind: 'feature_request', payload: { description: 'Расход по растениям', benefit: 'Планировать покупки' }, status: 'pending', revision: 'reviewed-text-2' }] }];
  mount(); await screen.findByText('Расход по растениям');
  expect(screen.getByText('Планировать покупки')).toBeInTheDocument();
  expect(assistantRequest.mock.calls.some(([, method]) => method === 'POST')).toBe(false);
  fireEvent.click(screen.getByRole('button', { name: 'Отправить запрос', exact: true }));
  await waitFor(() => expect(assistantRequest).toHaveBeenCalledWith('/proposals/request-1/confirm', 'POST', { revision: 'reviewed-text-2' }));
});

test('Enter sends once while Shift+Enter and composition do not send', async () => {
  const ordinary = assistantRequest.getMockImplementation();
  assistantRequest.mockImplementation(async (path, method, body) => path.endsWith('/messages') && method === 'POST'
    ? { id: 'sent', text: body.text, status: 'completed', answer: 'Ответ', attachments: [], proposals: [] } : ordinary(path, method, body));
  mount(); const input = await screen.findByRole('textbox', { name: 'Ваш вопрос' });
  fireEvent.change(input, { target: { value: 'Как растения?' } });
  fireEvent.keyDown(input, { key: 'Enter', shiftKey: true });
  fireEvent.keyDown(input, { key: 'Enter', isComposing: true });
  expect(assistantRequest.mock.calls.filter(([, m]) => m === 'POST')).toHaveLength(0);
  fireEvent.keyDown(input, { key: 'Enter' });
  await waitFor(() => expect(assistantRequest.mock.calls.filter(([, m]) => m === 'POST')).toHaveLength(1));
});

test('photos stay editable in the composer until explicit sending', async () => {
  mount(); await screen.findByRole('textbox', { name: 'Ваш вопрос' });
  const file = new File(['plant'], 'my-rose.jpg', { type: 'image/jpeg' });
  fireEvent.change(screen.getByLabelText('Приложить фото'), { target: { files: [file] } });
  expect(screen.getByText('my-rose.jpg')).toBeInTheDocument();
  expect(screen.getByLabelText('Приложить фото').closest('form')).toHaveClass('assistant-compose');
  expect(assistantRequest.mock.calls.some(([, m]) => m === 'POST')).toBe(false);
  fireEvent.click(screen.getByRole('button', { name: 'Удалить фото' }));
  expect(screen.queryByText('my-rose.jpg')).not.toBeInTheDocument();
});
