package ru.growerhub.backend.shop.contract;

public class ShopException extends RuntimeException {
    private final String code;
    private final Long retryAfter;
    public ShopException(String code, String detail) { this(code, detail, null); }
    public ShopException(String code, String detail, Long retryAfter) {
        super(detail); this.code = code; this.retryAfter = retryAfter;
    }
    public String getCode() { return code; }
    public Long getRetryAfter() { return retryAfter; }
}
