package ru.growerhub.backend.shop;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.notification.contract.TelegramData;
import ru.growerhub.backend.shop.contract.ShopData;
import ru.growerhub.backend.shop.engine.ShopService;

@Service
public class ShopFacade {
    private final ShopService shop;
    public ShopFacade(ShopService shop) { this.shop = shop; }
    public ShopData.Catalog catalog() { return shop.catalog(); }
    @Transactional
    public ShopData.Receipt submit(ShopData.Submission input, String clientAddress) { return shop.submit(input, clientAddress); }
    @Transactional(readOnly = true)
    public ShopData.Requests list(AuthenticatedUser user, ShopData.Status status, int page, int size) { return shop.list(user, status, page, size); }
    @Transactional(readOnly = true)
    public ShopData.Request get(AuthenticatedUser user, Long id) { return shop.get(user, id); }
    @Transactional
    public ShopData.Request update(AuthenticatedUser user, Long id, ShopData.Status status) { return shop.update(user, id, status); }
    @Transactional
    public ShopData.Request retry(AuthenticatedUser user, Long id) { return shop.retry(user, id); }
    @Transactional
    public List<TelegramData.Delivery> claimNotifications() { return shop.claim(); }
    @Transactional
    public void finishNotification(TelegramData.Delivery delivery, TelegramData.Result result) { shop.finish(delivery, result); }
}
