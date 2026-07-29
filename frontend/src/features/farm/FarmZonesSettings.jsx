import { useCallback, useEffect, useState } from 'react';
import {
  createGreenhouse,
  createUserFarm,
  deleteGreenhouse,
  deleteUserFarm,
  fetchFarmsOverview,
  updateGreenhouse,
  updateUserFarm,
} from '../../api/selfService';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import Button from '../../components/ui/Button';
import { listOrEmpty, overviewFarms } from './farmModel';
import { translateApp } from '../../locales/i18n';
import './FarmZonesSettings.css';

function FarmZonesSettings() {
  const [overview, setOverview] = useState(null);
  const [farmDrafts, setFarmDrafts] = useState({});
  const [greenhouseDrafts, setGreenhouseDrafts] = useState({});
  const [newFarmName, setNewFarmName] = useState('');
  const [newGreenhouseNames, setNewGreenhouseNames] = useState({});
  const [busy, setBusy] = useState('loading');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const applyOverview = useCallback((payload) => {
    const normalized = payload && typeof payload === 'object' ? payload : {};
    const farms = overviewFarms(normalized);
    setOverview(normalized);
    setFarmDrafts(Object.fromEntries(farms.map((farm) => [farm.id, {
      name: farm.name || '',
      enabled: farm.enabled !== false,
    }])));
    setGreenhouseDrafts(Object.fromEntries(
      farms.flatMap((farm) => listOrEmpty(farm.greenhouses))
        .map((greenhouse) => [greenhouse.id, {
          farm_id: greenhouse.farm_id,
          name: greenhouse.name || '',
          enabled: greenhouse.enabled !== false,
        }]),
    ));
  }, []);

  const load = useCallback(async () => {
    setBusy('loading');
    setError('');
    try {
      applyOverview(await fetchFarmsOverview());
    } catch (requestError) {
      setError(requestError?.message || translateApp('Не удалось загрузить зоны'));
    } finally {
      setBusy('');
    }
  }, [applyOverview]);

  useEffect(() => {
    load();
  }, [load]);

  const runAction = async (key, action, message) => {
    setBusy(key);
    setError('');
    setNotice('');
    try {
      const payload = await action();
      if (payload?.farms) {
        applyOverview(payload);
      } else {
        await load();
      }
      setNotice(message);
      return true;
    } catch (requestError) {
      setError(requestError?.message || translateApp('Не удалось сохранить изменения'));
      return false;
    } finally {
      setBusy('');
    }
  };

  const farms = overviewFarms(overview);

  const patchFarm = (farmId, patch) => {
    setFarmDrafts((current) => ({
      ...current,
      [farmId]: { ...current[farmId], ...patch },
    }));
  };

  const patchGreenhouse = (greenhouseId, patch) => {
    setGreenhouseDrafts((current) => ({
      ...current,
      [greenhouseId]: { ...current[greenhouseId], ...patch },
    }));
  };

  const addFarm = async (event) => {
    event.preventDefault();
    const name = newFarmName.trim();
    if (!name) return;
    const saved = await runAction(
      'farm:create',
      () => createUserFarm({ name, enabled: true }),
      translateApp('Ферма создана'),
    );
    if (saved) setNewFarmName('');
  };

  const removeFarm = (farm) => {
    if (!window.confirm(translateApp(
      'Удалить ферму «{{value1}}» со всеми теплицами? Растения останутся без размещения.',
      { value1: farm.name },
    ))) return;
    runAction(
      `farm:${farm.id}:delete`,
      () => deleteUserFarm(farm.id),
      translateApp('Ферма удалена'),
    );
  };

  const addGreenhouse = async (event, farm) => {
    event.preventDefault();
    const name = String(newGreenhouseNames[farm.id] || '').trim();
    if (!name) return;
    const saved = await runAction(
      `farm:${farm.id}:greenhouse:create`,
      () => createGreenhouse(farm.id, { name, enabled: true }),
      translateApp('Теплица создана'),
    );
    if (saved) {
      setNewGreenhouseNames((current) => ({ ...current, [farm.id]: '' }));
    }
  };

  const removeGreenhouse = (greenhouse) => {
    if (!window.confirm(translateApp(
      'Удалить теплицу «{{value1}}»? Растения останутся без теплицы.',
      { value1: greenhouse.name },
    ))) return;
    runAction(
      `greenhouse:${greenhouse.id}:delete`,
      () => deleteGreenhouse(greenhouse.id),
      translateApp('Теплица удалена'),
    );
  };

  if (busy === 'loading' && !overview) {
    return <AppPageState kind="loading" title={translateApp('Загружаем зоны…')} />;
  }

  return (
    <div className="farm-zones-settings">
      <AppPageHeader title={translateApp('Зоны')} />
      <p className="farm-zones-settings__intro">
        {translateApp('Ферма — это помещение. В каждой ферме можно создать несколько теплиц и переносить их между помещениями.')}
      </p>
      {error ? <AppPageState kind="error" title={error} /> : null}
      {notice ? <div className="farm-zones-settings__notice" role="status">{notice}</div> : null}

      <form className="farm-zones-settings__create" onSubmit={addFarm}>
        <label>
          <span>{translateApp('Новая ферма')}</span>
          <input
            value={newFarmName}
            onChange={(event) => setNewFarmName(event.target.value)}
            placeholder={translateApp('Например, Основное помещение')}
            maxLength="120"
          />
        </label>
        <Button
          type="submit"
          variant="primary"
          disabled={!newFarmName.trim()}
          isLoading={busy === 'farm:create'}
        >
          {translateApp('Добавить ферму')}
        </Button>
      </form>

      {farms.length === 0 ? (
        <AppPageState kind="empty" title={translateApp('Ферм пока нет')} />
      ) : (
        <div className="farm-zones-settings__farms">
          {farms.map((farm) => {
            const farmDraft = farmDrafts[farm.id] || {
              name: farm.name,
              enabled: farm.enabled,
            };
            return (
              <section className="farm-zones-settings__farm" key={farm.id}>
                <div className="farm-zones-settings__farm-fields">
                  <label>
                    <span>{translateApp('Название фермы')}</span>
                    <input
                      value={farmDraft.name}
                      onChange={(event) => patchFarm(farm.id, { name: event.target.value })}
                      maxLength="120"
                    />
                  </label>
                  <label className="farm-zones-settings__toggle">
                    <input
                      type="checkbox"
                      checked={farmDraft.enabled}
                      onChange={(event) => patchFarm(farm.id, { enabled: event.target.checked })}
                    />
                    <span>{translateApp('Активна')}</span>
                  </label>
                  <Button
                    size="sm"
                    disabled={!farmDraft.name.trim()}
                    isLoading={busy === `farm:${farm.id}:save`}
                    onClick={() => runAction(
                      `farm:${farm.id}:save`,
                      () => updateUserFarm(farm.id, {
                        name: farmDraft.name.trim(),
                        enabled: farmDraft.enabled,
                      }),
                      translateApp('Ферма сохранена'),
                    )}
                  >
                    {translateApp('Сохранить')}
                  </Button>
                  <Button
                    size="sm"
                    variant="danger"
                    isLoading={busy === `farm:${farm.id}:delete`}
                    onClick={() => removeFarm(farm)}
                  >
                    {translateApp('Удалить')}
                  </Button>
                </div>

                <div className="farm-zones-settings__greenhouses">
                  {listOrEmpty(farm.greenhouses).map((greenhouse) => {
                    const draft = greenhouseDrafts[greenhouse.id] || {
                      farm_id: farm.id,
                      name: greenhouse.name,
                      enabled: greenhouse.enabled,
                    };
                    return (
                      <article key={greenhouse.id}>
                        <label>
                          <span>{translateApp('Название теплицы')}</span>
                          <input
                            value={draft.name}
                            onChange={(event) => patchGreenhouse(
                              greenhouse.id,
                              { name: event.target.value },
                            )}
                            maxLength="120"
                          />
                        </label>
                        <label>
                          <span>{translateApp('Ферма')}</span>
                          <select
                            value={draft.farm_id}
                            onChange={(event) => patchGreenhouse(
                              greenhouse.id,
                              { farm_id: Number(event.target.value) },
                            )}
                          >
                            {farms.map((targetFarm) => (
                              <option key={targetFarm.id} value={targetFarm.id}>
                                {targetFarm.name}
                              </option>
                            ))}
                          </select>
                        </label>
                        <label className="farm-zones-settings__toggle">
                          <input
                            type="checkbox"
                            checked={draft.enabled}
                            onChange={(event) => patchGreenhouse(
                              greenhouse.id,
                              { enabled: event.target.checked },
                            )}
                          />
                          <span>{translateApp('Активна')}</span>
                        </label>
                        <div className="farm-zones-settings__actions">
                          <Button
                            size="sm"
                            disabled={!draft.name.trim()}
                            isLoading={busy === `greenhouse:${greenhouse.id}:save`}
                            onClick={() => runAction(
                              `greenhouse:${greenhouse.id}:save`,
                              () => updateGreenhouse(greenhouse.id, {
                                farm_id: draft.farm_id,
                                name: draft.name.trim(),
                                enabled: draft.enabled,
                              }),
                              translateApp('Теплица сохранена'),
                            )}
                          >
                            {translateApp('Сохранить')}
                          </Button>
                          <Button
                            size="sm"
                            variant="danger"
                            isLoading={busy === `greenhouse:${greenhouse.id}:delete`}
                            onClick={() => removeGreenhouse(greenhouse)}
                          >
                            {translateApp('Удалить')}
                          </Button>
                        </div>
                      </article>
                    );
                  })}
                </div>

                <form
                  className="farm-zones-settings__add-greenhouse"
                  onSubmit={(event) => addGreenhouse(event, farm)}
                >
                  <label>
                    <span>{translateApp('Новая теплица')}</span>
                    <input
                      value={newGreenhouseNames[farm.id] || ''}
                      onChange={(event) => setNewGreenhouseNames((current) => ({
                        ...current,
                        [farm.id]: event.target.value,
                      }))}
                      placeholder={translateApp('Например, Южная теплица')}
                      maxLength="120"
                    />
                  </label>
                  <Button
                    type="submit"
                    size="sm"
                    variant="secondary"
                    disabled={!String(newGreenhouseNames[farm.id] || '').trim()}
                    isLoading={busy === `farm:${farm.id}:greenhouse:create`}
                  >
                    {translateApp('Добавить теплицу')}
                  </Button>
                </form>
              </section>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default FarmZonesSettings;
