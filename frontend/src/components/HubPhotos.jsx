import { getPageContent } from '../content/pages';

function HubPhotos({ type, compact = false }) {
  const hub = getPageContent().platformContent[type];

  return <div className={`hub-photos${compact ? ' hub-photos--compact' : ''}`}>
    <div className="hub-photos__grid">
      {hub.photos.map((photo) => <figure key={photo.src}>
        <a href={hub.photos_source_url} target="_blank" rel="noreferrer">
          <img src={photo.src} alt={photo.alt} width={photo.width} height={photo.height} loading="lazy" decoding="async" />
        </a>
        <figcaption>{photo.label}</figcaption>
      </figure>)}
    </div>
    <p className="hub-photos__caption">{hub.photos_caption}{' '}
      <a href={hub.photos_source_url} target="_blank" rel="noreferrer">{hub.photos_source_label}</a>
    </p>
  </div>;
}

export default HubPhotos;
