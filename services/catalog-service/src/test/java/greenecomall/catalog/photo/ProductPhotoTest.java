package greenecomall.catalog.photo;

import com.sun.net.httpserver.HttpServer;
import greenecomall.catalog.domain.Category;
import greenecomall.catalog.domain.City;
import greenecomall.catalog.domain.Country;
import greenecomall.catalog.domain.PhotoStyle;
import greenecomall.catalog.domain.Product;
import greenecomall.catalog.domain.ProductImage;
import greenecomall.catalog.domain.ProductUnit;
import greenecomall.catalog.domain.Shop;
import greenecomall.catalog.repo.ProductImageRepository;
import greenecomall.catalog.repo.ProductRepository;
import greenecomall.common.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/** Фото товара: загрузка → варианты ИИ → публикация; промпт по названию/описанию; формат запроса к Gemini. */
@ExtendWith(MockitoExtension.class)
class ProductPhotoTest {

    private static final byte[] PNG = png();

    @Mock private ProductRepository products;
    @Mock private ProductImageRepository images;
    @TempDir Path media;

    private final List<ProductImage> saved = new ArrayList<>();
    private ProductPhotoService service;
    private Product product;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PhotoProperties props = new PhotoProperties(media, "https://cdn.test/media", 10, null);
        service = new ProductPhotoService(products, images, new LocalImageStorage(props), props);
        Country country = new Country("Kyrgyzstan", "KG");
        Shop shop = new Shop(owner, "Home Decor", null, country, new City(country, "Bishkek", null, null, null));
        product = new Product(shop, new Category(null, "Декор", "decor", 0), "Ваза керамическая",
                "Ручная работа, высота 25 см, матовая глазурь молочного цвета", ProductUnit.PCS, 150_000, "KGS");
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        lenient().when(products.findById(product.getId())).thenReturn(Optional.of(product));
        lenient().when(images.maxSort(any())).thenReturn(-10);
        lenient().when(images.save(any())).thenAnswer(inv -> {
            ProductImage image = inv.getArgument(0);
            ReflectionTestUtils.setField(image, "id", UUID.randomUUID());
            saved.add(image);
            lenient().when(images.findById(image.getId())).thenReturn(Optional.of(image));
            lenient().when(images.findWithLockById(image.getId())).thenReturn(Optional.of(image));
            return image;
        });
    }

    @Test
    void upload_alwaysMakesCoverPlusTheStylesTheShopChose() {
        List<ProductImage> result = service.upload(product.getId(), owner, PNG,
                List.of(PhotoStyle.WITH_PERSON, PhotoStyle.STUDIO), "  девушка в  бежевом пальто ");

        assertThat(result).extracting(ProductImage::getStyle)
                .as("обложка всегда, дубли стилей не повторяются")
                .containsExactly(PhotoStyle.STUDIO, PhotoStyle.WITH_PERSON, null);
        assertThat(result).extracting(ProductImage::getKind)
                .containsExactly(ProductImage.Kind.AI, ProductImage.Kind.AI, ProductImage.Kind.ORIGINAL);
        assertThat(result.get(1).getWish()).isEqualTo("девушка в бежевом пальто");
        ProductImage original = result.get(2);
        assertThat(original.isVisible()).as("оригинал виден сразу").isTrue();
        assertThat(original.getUrl()).startsWith("https://cdn.test/media/products/" + product.getId() + "/").endsWith(".png");
        assertThat(result.get(0).getStatus()).isEqualTo(ProductImage.Status.PENDING);
        assertThat(result.get(0).isVisible()).isFalse();
        assertThat(result.get(0).getSort()).as("обложка первой").isLessThan(original.getSort());
    }

    @Test
    void upload_rejectsNonImagesAndOtherShops() {
        assertThatThrownBy(() -> service.upload(product.getId(), owner, "<script>alert(1)</script>".getBytes(), null, null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.upload(product.getId(), UUID.randomUUID(), PNG, null, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void shopCanOrderOneMoreStyleFromTheSameOriginal() {
        ProductImage original = service.upload(product.getId(), owner, PNG, null, null).get(1);

        ProductImage inHands = service.generate(product.getId(), original.getId(), owner, PhotoStyle.IN_HANDS, null);

        assertThat(inHands.getStyle()).isEqualTo(PhotoStyle.IN_HANDS);
        assertThat(inHands.getStatus()).isEqualTo(ProductImage.Status.PENDING);
        assertThat(inHands.getSourceImageId()).isEqualTo(original.getId());
    }

    @Test
    void newStyleCannotBeOrderedFromAnAiPhoto() {
        ProductImage studio = service.upload(product.getId(), owner, PNG, null, null).getFirst();

        assertThatThrownBy(() -> service.generate(product.getId(), studio.getId(), owner, PhotoStyle.GIFT, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void readyStudioPhoto_replacesOriginalOnStorefront() {
        List<ProductImage> result = service.upload(product.getId(), owner, PNG, List.of(PhotoStyle.INTERIOR), null);
        ProductImage studio = result.get(0);
        ProductImage original = result.get(2);
        AiPhotoJob job = new AiPhotoJob(service, (data, mime, prompt) -> new ProductPhotoAi.Rendered(PNG, "image/png"));

        job.process(studio.getId());

        assertThat(studio.isVisible()).isTrue();
        assertThat(original.isVisible()).isFalse();
    }

    @Test
    void aiFailure_isRetriedThenGivesUpKeepingOriginal() {
        List<ProductImage> result = service.upload(product.getId(), owner, PNG, List.of(PhotoStyle.INTERIOR), null);
        ProductImage scene = result.get(1);
        AiPhotoJob job = new AiPhotoJob(service, (data, mime, prompt) -> {
            throw new IllegalStateException("quota exceeded");
        });

        for (int i = 0; i < ProductImage.MAX_ATTEMPTS; i++) {
            job.process(scene.getId());
        }

        assertThat(scene.getStatus()).isEqualTo(ProductImage.Status.FAILED);
        assertThat(scene.getError()).contains("quota exceeded");
        assertThat(result.get(2).isVisible()).isTrue();
    }

    @Test
    void prompt_describesTheActualProductAndForbidsRedrawingIt() {
        String interior = PhotoPromptBuilder.build(PhotoStyle.INTERIOR, product, null);
        String studio = PhotoPromptBuilder.build(PhotoStyle.STUDIO, product, null);

        assertThat(interior).contains("Ваза керамическая", "матовая глазурь молочного цвета", "Do not redraw");
        assertThat(studio).contains("Ваза керамическая", "#F8F5F0", "Do not redraw");
    }

    @Test
    void prompt_allowsPeopleOnlyInPeopleStylesAndCarriesTheShopWish() {
        String withPerson = PhotoPromptBuilder.build(PhotoStyle.WITH_PERSON, product, "девушка в бежевом пальто");
        String flatLay = PhotoPromptBuilder.build(PhotoStyle.FLAT_LAY, product, null);

        assertThat(withPerson).contains("девушка в бежевом пальто", "adult", "never a child")
                .doesNotContain("No people");
        assertThat(flatLay).contains("No people, faces or hands in the frame.");
    }

    @Test
    void gemini_sendsSourcePhotoWithPromptAndReturnsGeneratedImage() throws Exception {
        AtomicReference<String> sent = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            sent.set(exchange.getRequestURI().getPath() + "\n"
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = ("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"},{\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\""
                    + Base64.getEncoder().encodeToString(PNG) + "\"}}]}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        try {
            PhotoProperties props = new PhotoProperties(media, null, null,
                    new PhotoProperties.Ai("gemini", true, "key", null));
            GeminiProductPhotoAi gemini = new GeminiProductPhotoAi(props, JsonMapper.builder().build(),
                    "http://localhost:" + server.getAddress().getPort());

            ProductPhotoAi.Rendered rendered = gemini.render(PNG, "image/png", "studio photo of a vase");

            assertThat(rendered.data()).isEqualTo(PNG);
            String[] request = sent.get().split("\n", 2);
            assertThat(request[0]).isEqualTo("/v1beta/models/gemini-2.5-flash-image:generateContent");
            JsonNode parts = JsonMapper.builder().build().readTree(request[1]).path("contents").path(0).path("parts");
            assertThat(parts.path(0).path("text").asString()).isEqualTo("studio photo of a vase");
            assertThat(parts.path(1).path("inline_data").path("data").asString())
                    .isEqualTo(Base64.getEncoder().encodeToString(PNG));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void storage_rejectsKeysOutsideProductFolders() {
        LocalImageStorage storage = new LocalImageStorage(new PhotoProperties(media, null, null, null));
        assertThatThrownBy(() -> storage.get("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.put("products/x/../../a.png", PNG, "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] png() {
        byte[] data = new byte[64];
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(header, 0, data, 0, header.length);
        return data;
    }
}
