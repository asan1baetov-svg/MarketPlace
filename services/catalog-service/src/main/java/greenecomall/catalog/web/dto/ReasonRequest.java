package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Причина отказа/приостановки — переиспользуется для shop reject/suspend и product reject. */
public record ReasonRequest(@NotBlank String reason) {
}
