export default {
  ru: import.meta.glob('../../content/articles/*.md', {
    query: '?raw',
    import: 'default',
  }),
  en: import.meta.glob('../../content/en/articles/*.md', {
    query: '?raw',
    import: 'default',
  }),
};
