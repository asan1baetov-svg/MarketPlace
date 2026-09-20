package greenecomall.finance.web.dto;

public record ReasonRequest(String reason) {

    public String reasonOrDefault(String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }
}
