import { useEffect } from 'react';
import {
  DEFAULT_OG_IMAGE,
  SITE_URL,
  toCanonicalUrl,
} from '../domain/siteConfig';

const setMeta = (selector, attributes) => {
  let element = document.head.querySelector(selector);

  if (!element) {
    element = document.createElement('meta');
    document.head.appendChild(element);
  }

  Object.entries(attributes).forEach(([name, value]) => element.setAttribute(name, value));
};

const setCanonical = (path) => {
  const existing = document.head.querySelector('link[rel="canonical"]');

  if (path === null) {
    existing?.remove();
    return;
  }

  const canonical = existing || document.createElement('link');
  canonical.setAttribute('rel', 'canonical');
  canonical.setAttribute('href', toCanonicalUrl(path));
  if (!existing) {
    document.head.appendChild(canonical);
  }
};

export default function useSeoMeta({
  title,
  description,
  path,
  type = 'website',
  image = DEFAULT_OG_IMAGE,
  robots = 'index,follow',
  jsonLd = [],
}) {
  useEffect(() => {
    const canonical = path === null ? null : toCanonicalUrl(path);
    const imageUrl = image.startsWith('http') ? image : `${SITE_URL}${image}`;

    document.title = title;
    setMeta('meta[name="description"]', { name: 'description', content: description });
    setMeta('meta[name="robots"]', { name: 'robots', content: robots });
    setMeta('meta[property="og:title"]', { property: 'og:title', content: title });
    setMeta('meta[property="og:description"]', { property: 'og:description', content: description });
    setMeta('meta[property="og:type"]', { property: 'og:type', content: type });
    setMeta('meta[property="og:image"]', { property: 'og:image', content: imageUrl });

    const ogUrl = document.head.querySelector('meta[property="og:url"]');
    if (canonical) {
      setMeta('meta[property="og:url"]', { property: 'og:url', content: canonical });
    } else {
      ogUrl?.remove();
    }

    setCanonical(path);

    document.head.querySelectorAll('script[data-growerhub-jsonld="true"]').forEach((item) => item.remove());
    jsonLd.filter(Boolean).forEach((item) => {
      const script = document.createElement('script');
      script.type = 'application/ld+json';
      script.dataset.growerhubJsonld = 'true';
      script.textContent = JSON.stringify(item);
      document.head.appendChild(script);
    });

    return () => {
      document.head.querySelectorAll('script[data-growerhub-jsonld="true"]').forEach((item) => item.remove());
    };
  }, [description, image, jsonLd, path, robots, title, type]);
}
