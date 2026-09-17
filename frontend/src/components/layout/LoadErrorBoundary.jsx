import { Component } from 'react';
import { translateCommon } from '../../locales/i18n';

export default class LoadErrorBoundary extends Component {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    if (!this.state.failed) return this.props.children;

    return (
      <section className="section" role="alert">
        <h1>{translateCommon('error.load.title')}</h1>
        <p>{translateCommon('error.load.description')}</p>
        <button type="button" className="hero-cta" onClick={() => window.location.reload()}>
          {translateCommon('error.load.reload')}
        </button>
      </section>
    );
  }
}
