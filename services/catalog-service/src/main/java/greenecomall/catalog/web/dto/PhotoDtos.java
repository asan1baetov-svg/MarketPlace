package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.PhotoStyle;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PhotoDtos {

    private PhotoDtos() {
    }

    /**
     * @param code       значение для запросов ({@code WITH_PERSON} и т.д.)
     * @param withPeople в кадре будет человек — предупредить магазин в интерфейсе
     */
    public record PhotoStyleResponse(String code, String label, String description, boolean withPeople) {
        public static PhotoStyleResponse from(PhotoStyle style) {
            return new PhotoStyleResponse(style.name(), style.label(), style.description(), style.withPeople());
        }
    }

    /** @param wish пожелание к кадру своими словами, например «девушка в бежевом пальто на улице» */
    public record GeneratePhotoRequest(@NotNull PhotoStyle style, @Size(max = 200) String wish) {
    }
}
