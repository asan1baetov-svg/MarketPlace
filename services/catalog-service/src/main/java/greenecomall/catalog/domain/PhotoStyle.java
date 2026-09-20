package greenecomall.catalog.domain;

/**
 * Стили кадра, из которых магазин выбирает, каким сделать фото товара. Подпись и пояснение — для
 * интерфейса магазина; {@code scene} — что ИИ делает вокруг товара (сам товар не меняется ни в одном стиле).
 */
public enum PhotoStyle {

    STUDIO("Студия", "Товар на фирменном кремовом фоне — для обложки карточки", false, """
            Replace the background with a seamless warm cream studio backdrop (#F8F5F0). Soft diffused daylight, \
            a gentle natural shadow under the product, product centred and filling about 70% of the frame."""),

    INTERIOR("В интерьере", "Уютная обстановка, где такой товар обычно стоит или используется", false, """
            Place the product in a realistic, tasteful interior where such an item naturally belongs — work out \
            which room and surface fit best from its name and description. Warm natural window light, calm neutral \
            tones, uncluttered composition, shallow depth of field, the product is the clear hero."""),

    WITH_PERSON("С человеком", "Взрослая модель пользуется товаром: носит, держит, сидит, работает с ним", true, """
            Show one adult person naturally using the product the way it is meant to be used — wearing it, holding \
            it, sitting on it or working with it, depending on what the product is. Relaxed, natural pose, modern \
            everyday styling, soft natural light, candid lifestyle feel. The product must stay the focus and be \
            fully recognisable. The person is a generic, non-famous adult."""),

    IN_HANDS("В руках", "Крупный план: руки держат товар", true, """
            Close-up of adult hands holding or presenting the product, only hands and forearms in frame, neutral \
            sleeves, soft natural light, softly blurred warm background."""),

    FLAT_LAY("Раскладка сверху", "Вид сверху, товар в композиции с подходящими предметами", false, """
            Top-down flat lay on a light natural surface (linen, light wood or stone) with a few small complementary \
            props that suit the product, balanced composition with generous empty space, soft even light."""),

    OUTDOOR("На улице", "Природа или город при дневном свете", false, """
            Place the product outdoors in a setting that suits it — nature, a terrace, a park or a city street, \
            depending on what the product is. Soft daylight, gentle background blur, fresh and natural mood."""),

    DETAIL("Детали крупно", "Макро: фактура, материал, фурнитура, швы", false, """
            Macro close-up that shows the material, texture and craftsmanship details of the product, very shallow \
            depth of field, soft side light that reveals texture."""),

    GIFT("Подарок", "Праздничная подача: упаковка, лента, тёплый свет", false, """
            Festive gift presentation: the product next to elegant minimal gift wrapping, a ribbon and a few subtle \
            seasonal accents, warm cosy light, calm and premium feel.""");

    private final String label;
    private final String description;
    private final boolean withPeople;
    private final String scene;

    PhotoStyle(String label, String description, boolean withPeople, String scene) {
        this.label = label;
        this.description = description;
        this.withPeople = withPeople;
        this.scene = scene;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** В кадре есть человек (или руки) — для остальных стилей людей в кадре быть не должно. */
    public boolean withPeople() {
        return withPeople;
    }

    public String scene() {
        return scene;
    }
}
