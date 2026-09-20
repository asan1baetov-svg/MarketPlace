package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.DeliveryZone;

import java.util.UUID;

public record ZoneResponse(UUID id, UUID cityId, String name, Integer radiusM) {

    public static ZoneResponse from(DeliveryZone zone) {
        return new ZoneResponse(zone.getId(), zone.getCity().getId(), zone.getName(), zone.getRadiusM());
    }
}
