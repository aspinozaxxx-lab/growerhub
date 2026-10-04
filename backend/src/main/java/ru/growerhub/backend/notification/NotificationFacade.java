package ru.growerhub.backend.notification;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.notification.contract.TelegramData;
import ru.growerhub.backend.notification.engine.TelegramService;

@Service
public class NotificationFacade {
    private final TelegramService telegram;
    public NotificationFacade(TelegramService telegram) { this.telegram = telegram; }
    @Transactional(readOnly = true)
    public TelegramData.Status status(AuthenticatedUser user) { return telegram.status(user); }
    @Transactional
    public TelegramData.Link link(AuthenticatedUser user) { return telegram.link(user); }
    @Transactional
    public TelegramData.Status confirm(AuthenticatedUser user, String confirmation) { return telegram.confirm(user, confirmation); }
    @Transactional
    public TelegramData.Status preferences(AuthenticatedUser user, TelegramData.Preferences preferences) { return telegram.preferences(user, preferences); }
    @Transactional
    public void disconnect(AuthenticatedUser user) { telegram.disconnect(user); }
    @Transactional
    public void test(AuthenticatedUser user) { telegram.test(user); }
    @Transactional
    public String receive(TelegramData.Update update) { return telegram.receive(update); }
    @Transactional
    public void prepare() { telegram.prepare(); }
    @Transactional
    public List<TelegramData.Delivery> claim() { return telegram.claim(); }
    @Transactional
    public void finish(TelegramData.Delivery delivery, TelegramData.Result result) { telegram.finish(delivery, result); }
}
