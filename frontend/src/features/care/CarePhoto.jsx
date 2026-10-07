import { useEffect, useState } from 'react';
import { downloadJournalPhotoBlob } from '../../api/plantJournal';
import { translateApp as t } from '../../locales/i18n';
import Modal from '../../components/ui/Modal';

export default function CarePhoto({ id, alt = '', className = '', preview = false }) {
  const [loaded, setLoaded] = useState(null);
  const [open, setOpen] = useState(false);
  const url = loaded && loaded.id === id ? loaded.url : null;
  const [failedId, setFailedId] = useState(null);
  useEffect(() => {
    let live = true; let objectUrl;
    if (id) downloadJournalPhotoBlob(id).then(blob => {
      if (!live) return;
      objectUrl = URL.createObjectURL(blob); setLoaded({ id, url: objectUrl });
    }).catch(() => { if (live) setFailedId(id); });
    return () => { live = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [id]);
  if (!id) return <span className={`care-photo-placeholder ${className}`} aria-hidden="true">🌿</span>;
  return url ? (preview ? <><button type="button" className="care-photo-open" aria-label={t('Открыть фотографию')} onClick={() => setOpen(true)}><img className={`ym-hide-content ${className}`} src={url} alt={alt} loading="lazy" /></button>{open && <Modal isOpen title={t('Фото')} onClose={() => setOpen(false)}><img className="care-photo-full ym-hide-content" src={url} alt={alt} /></Modal>}</> : <img className={`ym-hide-content ${className}`} src={url} alt={alt} loading="lazy" />)
    : <span className={`care-photo-placeholder ${className}`}>{failedId === id ? t('Фото недоступно') : t('Загрузка фото...')}</span>;
}
