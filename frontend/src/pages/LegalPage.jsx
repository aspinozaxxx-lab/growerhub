import { legalContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { getCurrentLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function LegalPage({ type }) {
  const isPrivacy = type === 'privacy';
  const locale = getCurrentLocale();
  const title = isPrivacy ? legalContent.privacy_title : legalContent.terms_title;
  const path = getPublicPath(isPrivacy ? 'privacy' : 'terms', locale);
  useSeoMeta({
    title,
    description: translatePublic('legal.description', { title }),
    path,
    robots: 'noindex,follow',
    locale,
  });

  if (!legalContent.reviewed || !legalContent.operator_name || !legalContent.operator_contact) {
    return (
      <div className="section legal-page">
        <div className="badge">{translatePublic('Информация обновляется')}</div>
        <h1>{title}</h1>
        <p>{translatePublic('Мы обновляем эту страницу. Если у вас есть вопрос о GrowerHub или работе с данными, напишите команде в Telegram.')}</p>
      </div>
    );
  }

  return (
    <div className="section legal-page">
      <h1>{title}</h1>
      <p>{translatePublic('Оператор:')} {legalContent.operator_name}. {translatePublic('Контакт:')} {legalContent.operator_contact}.</p>
      {isPrivacy ? (
        <>
          <h2>{translatePublic('Какие данные обрабатываются')}</h2><p>{translatePublic('Адрес электронной почты и идентификатор выбранного способа входа, технические данные подключений и устройств, события безопасности, настройки зон и автоматизаций.')}</p>
          <h2>{translatePublic('Для чего')}</h2><p>{translatePublic('Для входа, работы платформы, защиты пользовательского пространства, диагностики и поддержки по обращению пользователя.')}</p>
          <h2>{translatePublic('Секреты подключения')}</h2><p>{translatePublic('Одноразовый MQTT-пароль показывается при создании или ротации и не хранится в базе GrowerHub в открытом виде. Локальные данные доступа Home Assistant вводятся только в браузере для скачиваемого файла.')}</p>
          <h2>{translatePublic('Веб-аналитика')}</h2><p>{translatePublic('Яндекс Метрика и Google Analytics 4 помогают понимать посещаемость и этапы запуска платформы. В события передаются адрес страницы, язык интерфейса и неперсональные параметры сценария. Email, внутренние идентификаторы, IEEE и реквизиты MQTT в аналитику не передаются.')}</p>
          <h2>{translatePublic('Обращения')}</h2><p>{translatePublic('По вопросам данных и удаления аккаунта используйте контакт оператора.')}</p>
        </>
      ) : (
        <>
          <h2>{translatePublic('Ранний доступ')}</h2><p>{translatePublic('GrowerHub доступен бесплатно и без карты. Основные функции уже работают, а каталог устройств и сценариев постоянно расширяется. О важных изменениях сообщим заранее.')}</p>
          <h2>{translatePublic('Подключение оборудования')}</h2><p>{translatePublic('Для оборудования с водой и сетевым питанием соблюдайте электробезопасность, проверяйте нагрузку и предусмотрите физическое аварийное отключение.')}</p>
          <h2>{translatePublic('Поддержка')}</h2><p>{translatePublic('Если понадобится помощь, напишите нам в Telegram — подскажем с подключением, устройствами и настройкой функций на русском или английском.')}</p>
        </>
      )}
    </div>
  );
}

export default LegalPage;
