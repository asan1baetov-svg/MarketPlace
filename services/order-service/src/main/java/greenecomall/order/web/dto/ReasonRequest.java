package greenecomall.order.web.dto;

/** Тело для действий, которым нужна только причина (отмена, возврат). */
public record ReasonRequest(String reason) {

    public String reasonOrDefault(String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }
}
