export const buildSsoLoginUrl = (provider, target, locale, origin) => {
  const redirectUrl = new URL(target, origin);
  redirectUrl.searchParams.set('lang', locale);
  const redirectPath = `${redirectUrl.pathname}${redirectUrl.search}`;
  return `/api/auth/sso/${provider}/login?redirect_path=${encodeURIComponent(redirectPath)}`;
};
