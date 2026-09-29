import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { connectPushok, fetchPushokAvailability, fetchPushokConnection, retryPushokPairing } from '../api/pushokConnection';
import { translateApp } from '../locales/i18n';
import Button from './ui/Button';
import Modal from './ui/Modal';
import PushokPilot from './PushokPilot';
import './PushokConnect.css';
import { pushokError } from '../utils/pushokConnection';

export default function PushokConnect({ coordinator = null, onCreated, card = false }) {
  const [params, setParams] = useSearchParams();
  const [available, setAvailable] = useState(null);
  const [open, setOpen] = useState(false);
  const [hubId, setHubId] = useState(coordinator?.hub_id || '');
  const [name, setName] = useState(coordinator?.name || 'ПушОк');
  const [confirmed, setConfirmed] = useState(false);
  const [connection, setConnection] = useState(coordinator);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    fetchPushokAvailability().then((value) => { if (active) setAvailable(value.available); })
      .catch(() => { if (active) setAvailable(false); });
    return () => { active = false; };
  }, []);
  useEffect(() => {
    if (!coordinator && available && (params.get('connect') === 'pushok' || params.get('pilot') === 'pushok')) {
      setOpen(true);
      const next = new URLSearchParams(params); next.delete('connect'); next.delete('pilot');
      setParams(next, { replace: true });
    }
  }, [available, params, setParams, coordinator]);
  useEffect(() => {
    if (!open || !connection?.id || connection.connection_status !== 'PAIRING') return undefined;
    let active = true;
    const timer = window.setInterval(async () => {
      try {
        const current = await fetchPushokConnection(connection.id);
        if (active) setConnection(current);
      } catch (requestError) { if (active) setError(requestError.message); }
    }, 2000);
    return () => { active = false; window.clearInterval(timer); };
  }, [open, connection?.id, connection?.connection_status]);

  const submit = async (event) => {
    event.preventDefault();
    if (!confirmed || busy) return;
    setBusy(true); setError('');
    try {
      const next = connection?.id
        ? await retryPushokPairing(connection.id)
        : await connectPushok({ name: name.trim(), hub_id: hubId.trim(), confirm_access: true });
      setConnection(next); onCreated?.(next);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  };
  const show = () => {
    if (!coordinator) {
      setConnection(null); setHubId(''); setName('ПушОк'); setConfirmed(false); setError('');
    }
    setOpen(true);
  };
  if (available === false && !coordinator) return <PushokPilot />;
  const pairing = connection?.connection_status === 'PAIRING';
  const connected = connection?.connection_status === 'ACTIVE';
  return (
    <>
      {card ? (
        <button type="button" className="choice-card" disabled={!available} onClick={show}>
          <strong>{translateApp('Есть шлюз ПушОк')}</strong><span>{translateApp('Подключите через интернет: ID шлюза и кнопка сопряжения. Компьютер и Home Assistant не нужны.')}</span>
        </button>
      ) : <Button onClick={show} disabled={!available}>{translateApp(coordinator ? 'Повторить привязку ПушОк' : 'Подключить ПушОк')}</Button>}
      <Modal isOpen={open} onClose={() => setOpen(false)} title={translateApp('Подключить ПушОк')} presentation="sheet">
        <div className="pushok-connect">
          <div className="pushok-connect__intro"><img src="/content/equipment/pushok-pok100.jpg" alt="ПушОк POK100" width="88" height="88" />
            <p>{translateApp('Шлюз уже должен быть подключён к Wi-Fi и доступен в Управляторе через интернет.')}</p></div>
          {error ? <p role="alert">{error}</p> : null}
          {connected ? (
            <div role="status"><h3>{translateApp('ПушОк подключён')}</h3>
              <p>{translateApp('Устройства появятся в GrowerHub автоматически. Новые датчики и розетки добавляйте в Управляторе.')}</p>
              <Button variant="primary" onClick={() => setOpen(false)}>{translateApp('Готово')}</Button></div>
          ) : pairing ? (
            <p role="status">{translateApp('Подключаемся к шлюзу через облако. Обычно это занимает несколько секунд. Можно закрыть окно — подключение продолжится.')}</p>
          ) : (
            <form onSubmit={submit} className="ym-hide-content">
              {connection?.connection_error ? <p role="alert">{pushokError(connection.connection_error)}</p> : null}
              <label>{translateApp('ID шлюза из Управлятора')}
                <input value={hubId} onChange={(event) => setHubId(event.target.value)} placeholder="pushok-A1B2C3-1234" autoCapitalize="off" spellCheck={false} maxLength={80} required disabled={Boolean(connection?.id)} /></label>
              <label>{translateApp('Название подключения')}<input value={name} onChange={(event) => setName(event.target.value)} maxLength={120} required disabled={Boolean(connection?.id)} /></label>
              <label className="pushok-connect__consent"><input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} />
                <span>{translateApp('Это мой шлюз. Разрешаю GrowerHub получать показания и управлять его устройствами.')}</span></label>
              <p>{translateApp('Все устройства этого шлюза появятся в вашем аккаунте. Сценарии сами не включатся. Настраивайте автоматизацию одного устройства в одной системе, чтобы команды не мешали друг другу.')}</p>
              <p><strong>{translateApp('Дважды коротко нажмите кнопку сопряжения на шлюзе. Когда индикатор начнёт мигать, нажмите кнопку ниже.')}</strong></p>
              <Button type="submit" variant="primary" disabled={!confirmed} isLoading={busy}>{translateApp('Кнопка нажата — подключить')}</Button>
            </form>
          )}
        </div>
      </Modal>
    </>
  );
}
