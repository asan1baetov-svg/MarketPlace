package greenecomall.courier.web.dto;

import greenecomall.courier.domain.Courier;
import greenecomall.courier.domain.CourierEarning;
import greenecomall.courier.domain.DeliveryJob;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Запросы/ответы courier-service. */
public final class CourierDtos {

    private CourierDtos() {
    }

    public record IdResponse(UUID id) {
    }

    /** Пользователь-курьер — из access-JWT. */
    public record RegisterCourierRequest(
            @NotNull UUID countryId,
            @NotNull UUID cityId,
            Set<UUID> zoneIds,
            JsonNode vehicle) {
    }

    public record StatusRequest(@NotNull Courier.Status status) {
    }

    public record ReasonRequest(String reason) {
    }

    public record FailRequest(@NotBlank String reason) {
    }

    public record ManualAssignRequest(@NotNull UUID suborderId, @NotNull UUID courierId) {
    }

    public record CourierResponse(
            UUID id, UUID userId, UUID countryId, UUID cityId, Set<UUID> zoneIds, String status,
            String moderationStatus, String rejectionReason, BigDecimal rating) {

        public static CourierResponse from(Courier c) {
            return new CourierResponse(c.getId(), c.getUserId(), c.getCountryId(), c.getCityId(),
                    Set.copyOf(c.getZoneIds()), c.getStatus().name(), c.getModerationStatus().name(),
                    c.getRejectionReason(), c.getRating());
        }
    }

    public record DeliveryResponse(
            UUID suborderId, UUID orderId, UUID shopId, UUID cityId, String status, UUID courierId,
            Instant offerExpiresAt, boolean readyForPickup, String failureReason,
            Instant acceptedAt, Instant deliveredAt) {

        public static DeliveryResponse from(DeliveryJob j) {
            return new DeliveryResponse(j.getSuborderId(), j.getOrderId(), j.getShopId(), j.getCityId(),
                    j.getStatus().name(), j.getCourierId(), j.getOfferExpiresAt(), j.isReadyForPickup(),
                    j.getFailureReason(), j.getAcceptedAt(), j.getDeliveredAt());
        }
    }

    /** Карточка доставки для курьера: статус, откуда забрать, куда везти, что внутри. */
    public record DeliveryDetailsResponse(DeliveryResponse delivery, Pickup pickup, Dropoff dropoff) {

        public record Pickup(String shopName, String address, String phone) {
        }

        public record Dropoff(tools.jackson.databind.JsonNode address, long amountMinor, String currency,
                              String orderStatus, List<Item> items) {
        }

        public record Item(String name, int qty) {
        }
    }

    public record EarningsResponse(UUID courierId, Instant from, Instant to, long totalMinor, List<Item> items) {

        public record Item(UUID suborderId, long amountMinor, String currency, Instant createdAt) {
            public static Item from(CourierEarning e) {
                return new Item(e.getSuborderId(), e.getAmountMinor(), e.getCurrency(), e.getCreatedAt());
            }
        }
    }
}
