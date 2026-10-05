import { ShopCart, useShop } from '../features/shop';
import useSeoMeta from '../utils/useSeoMeta';

export default function ShopCartPage() {
  const { locale } = useShop();
  useSeoMeta({
    title: locale === 'en' ? 'Your kit basket — GrowerHub' : 'Корзина комплектов — GrowerHub',
    description: locale === 'en' ? 'Review your nursery kit and leave a request. No payment is needed now.' : 'Проверьте комплект для питомника и оставьте заявку. Оплачивать сейчас ничего не нужно.',
    path: locale === 'en' ? '/en/cart/' : '/korzina/', robots: 'noindex,nofollow', locale,
  });
  return <ShopCart />;
}
