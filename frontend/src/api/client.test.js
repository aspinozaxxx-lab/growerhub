import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, normalizeApiErrorMessage, registerAuthHandlers, resetApiSession } from './client';

describe('normalizeApiErrorMessage', () => {
  it('не показывает транслит из ответа API', () => {
    expect(normalizeApiErrorMessage(
      'Servis vremenno nedostupen',
      { status: 503 },
    )).toBe('Сервис временно недоступен');
  });

  it('сохраняет понятное сообщение на кириллице', () => {
    expect(normalizeApiErrorMessage('Координатор не найден', { status: 404 }))
      .toBe('Координатор не найден');
  });

  it('использует контекстный текст вместо английской ошибки', () => {
    expect(normalizeApiErrorMessage('Device not found', {
      status: 404,
      fallback: 'Не удалось загрузить устройство',
    })).toBe('Не удалось загрузить устройство');
  });
});

describe('razdelenie sessij API', () => {
  afterEach(() => { resetApiSession(); registerAuthHandlers({}); vi.unstubAllGlobals(); });
  const response = (status = 200) => ({ status, ok: status < 400 });
  const setup = (extra = {}) => {
    const handlers = { getMode: () => 'demo', getToken: (scope) => scope + '-token', expire: vi.fn(), onDemoAction: vi.fn(), ...extra };
    registerAuthHandlers(handlers);
    return handlers;
  };
  it('sohranjaet demo ot imeni akkaunta i menjaet fermu ot imeni demo', async () => {
    setup(); const request = vi.fn().mockResolvedValue(response()); vi.stubGlobal('fetch', request);
    await apiFetch('/api/demo/save', { method: 'POST' });
    await apiFetch('/api/plants', { method: 'POST' });
    expect(request.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer account-token');
    expect(request.mock.calls[1][1].headers.get('Authorization')).toBe('Bearer demo-token');
  });
  it('obnovljaet tolko demo token i zamenjaet staryj zagolovok pri povtore', async () => {
    const refresh = vi.fn().mockResolvedValue('fresh-demo'); setup({ refresh });
    const request = vi.fn().mockResolvedValueOnce(response(401)).mockResolvedValue(response()); vi.stubGlobal('fetch', request);
    await apiFetch('/api/plants', { headers: { Authorization: 'Bearer stale' } });
    expect(refresh).toHaveBeenCalledExactlyOnceWith('demo');
    expect(request.mock.calls[1][1].headers.get('Authorization')).toBe('Bearer fresh-demo');
  });
  it('ne vykhodit iz realnogo akkaunta pri istechenii demo', async () => {
    const handlers = setup({ refresh: vi.fn().mockResolvedValue(null) });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(401)));
    await expect(apiFetch('/api/plants')).rejects.toMatchObject({ code: 'SESSION_EXPIRED' });
    expect(handlers.expire).toHaveBeenCalledExactlyOnceWith('demo');
  });
  it('otbrasyvaet uspeshnyj otvet posle pereklyuchenija fermy', async () => {
    let complete; const handlers = setup();
    vi.stubGlobal('fetch', vi.fn(() => new Promise((resolve) => { complete = resolve; })));
    const pending = apiFetch('/api/plants', { method: 'POST' });
    const rejected = expect(pending).rejects.toMatchObject({ name: 'AbortError' });
    resetApiSession(); complete(response()); await rejected;
    expect(handlers.onDemoAction).not.toHaveBeenCalled();
  });
  it('ne povtorjaet staryj zapros i ne sbrasyvaet novuju sessiju posle refresh', async () => {
    let complete; const refresh = vi.fn(() => new Promise((resolve) => { complete = resolve; }));
    const handlers = setup({ refresh }); const request = vi.fn().mockResolvedValue(response(401)); vi.stubGlobal('fetch', request);
    const pending = apiFetch('/api/plants');
    const rejected = expect(pending).rejects.toMatchObject({ name: 'AbortError' });
    await vi.waitFor(() => expect(refresh).toHaveBeenCalled());
    resetApiSession(); complete('late-demo'); await rejected;
    expect(request).toHaveBeenCalledTimes(1); expect(handlers.expire).not.toHaveBeenCalled();
  });
});
