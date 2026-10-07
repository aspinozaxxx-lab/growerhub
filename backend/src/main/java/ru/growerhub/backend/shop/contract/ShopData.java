package ru.growerhub.backend.shop.contract;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class ShopData {
    private ShopData() { }
    public enum Kind { ORDER, CONSULTATION }
    public enum Status { NEW, PROCESSING, CONFIRMED, CLOSED }
    public enum Verification { PILOT, TESTED }
    public record Component(String id, int quantity) { }
    public record Offer(String id, String hubModel, String hubEquipmentId, String socketEquipmentId,
            int socketCount, long priceMinor, Verification verification, List<Component> components) {
        public Offer { components = components == null ? null : List.copyOf(components); }
    }
    public record Definition(String version, String currency, List<Offer> offers) { }
    public record Catalog(String version, String currency, List<Offer> offers, boolean acceptingRequests) { }
    public record Item(String offerId, int quantity) { }
    public record Customer(String name, String phone, String telegram) { }
    public record Pickup(String city, String code, String address) { }
    public record Submission(Kind kind, UUID idempotencyKey, String catalogVersion, List<Item> items,
            Customer customer, Pickup pickup, String comment, Boolean consent, String website) { }
    public record Receipt(String number, long totalMinor, String currency, LocalDateTime createdAt, Status status) { }
    public record SnapshotItem(String offerId, int quantity, long unitPriceMinor, String hubModel,
            String hubEquipmentId, String socketEquipmentId, int socketCount, Verification verification, List<Component> components) { }
    public record Notification(String status, int attempts, String lastError, LocalDateTime updatedAt) { }
    public record Request(Long id, String number, Kind kind, Status status, LocalDateTime createdAt,
            LocalDateTime updatedAt, long totalMinor, String currency, String catalogVersion,
            List<SnapshotItem> items, Customer customer, Pickup pickup, String comment, Notification notification) { }
    public record Requests(List<Request> requests, int page, int size, long totalElements, int totalPages) { }
}
