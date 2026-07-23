import { legalContent } from '../content/pages';
import useSeoMeta from '../utils/useSeoMeta';

function LegalPage({ type }) {
  const isPrivacy = type === 'privacy';
  const title = isPrivacy ? legalContent.privacy_title : legalContent.terms_title;
  const path = isPrivacy ? '/privacy/' : '/terms/';
  useSeoMeta({
    title,
    description: `${title} для пользователей платформы GrowerHub.`,
    path,
    robots: 'noindex,follow',
  });

  if (!legalContent.reviewed || !legalContent.operator_name || !legalContent.operator_contact) {
    return (
      <div className="section legal-page">
        <div className="badge">Информация обновляется</div>
        <h1>{title}</h1>
        <p>Мы обновляем эту страницу. Если у вас есть вопрос о GrowerHub или работе с данными, напишите команде в Telegram.</p>
      </div>
    );
  }

  return (
    <div className="section legal-page">
      <h1>{title}</h1>
      <p>Оператор: {legalContent.operator_name}. Контакт: {legalContent.operator_contact}.</p>
      {isPrivacy ? (
        <>
          <h2>Какие данные обрабатываются</h2><p>Адрес электронной почты и идентификатор выбранного способа входа, технические данные подключений и устройств, события безопасности, настройки зон и автоматизаций.</p>
          <h2>Для чего</h2><p>Для входа, работы платформы, защиты пользовательского пространства, диагностики и поддержки по обращению пользователя.</p>
          <h2>Секреты подключения</h2><p>Одноразовый MQTT-пароль показывается при создании или ротации и не хранится в базе GrowerHub в открытом виде. Локальные данные доступа Home Assistant вводятся только в браузере для скачиваемого файла.</p>
          <h2>Обращения</h2><p>По вопросам данных и удаления аккаунта используйте доменный контакт оператора.</p>
        </>
      ) : (
        <>
          <h2>Ранний доступ</h2><p>GrowerHub доступен бесплатно и без карты. Основные функции уже работают, а каталог устройств и сценариев постоянно расширяется. О важных изменениях сообщим заранее.</p>
          <h2>Подключение оборудования</h2><p>Для оборудования с водой и сетевым питанием соблюдайте электробезопасность, проверяйте нагрузку и предусмотрите физическое аварийное отключение.</p>
          <h2>Поддержка</h2><p>Если понадобится помощь, напишите нам в Telegram — подскажем с подключением, устройствами и настройкой функций.</p>
        </>
      )}
    </div>
  );
}

export default LegalPage;
