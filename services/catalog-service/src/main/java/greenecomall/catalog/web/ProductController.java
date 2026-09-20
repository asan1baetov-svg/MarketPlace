package greenecomall.catalog.web;

import greenecomall.catalog.domain.PhotoStyle;
import greenecomall.catalog.domain.Product;
import greenecomall.catalog.photo.ProductPhotoService;
import greenecomall.catalog.product.ProductService;
import greenecomall.catalog.text.ListingTextService;
import greenecomall.catalog.web.dto.IdResponse;
import greenecomall.catalog.web.dto.PhotoDtos.GeneratePhotoRequest;
import greenecomall.catalog.web.dto.PhotoDtos.PhotoStyleResponse;
import greenecomall.catalog.web.dto.ProductCreateRequest;
import greenecomall.catalog.web.dto.ProductImageAddRequest;
import greenecomall.catalog.web.dto.ProductImageResponse;
import greenecomall.catalog.web.dto.ProductResponse;
import greenecomall.catalog.web.dto.ProductUpdateRequest;
import greenecomall.catalog.web.dto.ProofreadDtos.ProofreadRequest;
import greenecomall.catalog.web.dto.ProofreadDtos.ProofreadResponse;
import greenecomall.catalog.web.dto.StockSetRequest;
import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.web.security.Authz;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Управление своим товаром магазином. Владелец — пользователь из access-JWT; владение проверяет
 * {@code ProductService}. Администратор действует без проверки владения ({@code callerUserId = null}).
 */
@RestController
public class ProductController {

    private final ProductService productService;
    private final ProductPhotoService photoService;
    private final ListingTextService listingText;

    public ProductController(ProductService productService, ProductPhotoService photoService,
                             ListingTextService listingText) {
        this.productService = productService;
        this.photoService = photoService;
        this.listingText = listingText;
    }

    /**
     * Предпросмотр корректуры для формы товара: что ИИ исправит в названии и описании. Те же правки
     * применяются автоматически при сохранении ({@code POST/PUT}).
     */
    @PostMapping("/products/proofread")
    public ProofreadResponse proofread(@Valid @RequestBody ProofreadRequest request, AuthPrincipal principal) {
        Authz.require(principal);
        ListingTextService.Result r = listingText.clean(request.name(), request.description());
        return new ProofreadResponse(r.name(), r.description(), r.changed());
    }

    /** Стили кадра для выбора магазином (обложка, в интерьере, с человеком, в руках…). */
    @GetMapping("/products/photo-styles")
    public List<PhotoStyleResponse> photoStyles() {
        return photoService.styles().stream().map(PhotoStyleResponse::from).toList();
    }

    /**
     * Загрузка фото с телефона/компьютера (multipart, поле {@code file}). {@code styles} — какие кадры
     * сделать ИИ (обложка делается всегда), {@code wish} — пожелание к кадру своими словами.
     * Варианты возвращаются в статусе PENDING и становятся READY через несколько секунд.
     */
    @PostMapping(value = "/products/{id}/images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<ProductImageResponse> upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
                                             @RequestParam(required = false) List<PhotoStyle> styles,
                                             @RequestParam(required = false) String wish,
                                             AuthPrincipal principal) throws IOException {
        return photoService.upload(id, caller(principal), file.getBytes(), styles, wish).stream()
                .map(ProductImageResponse::from).toList();
    }

    /** Заказать ещё один кадр в выбранном стиле из уже загруженного фото. */
    @PostMapping("/products/{id}/images/{imageId}/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductImageResponse generate(@PathVariable UUID id, @PathVariable UUID imageId,
                                         @Valid @RequestBody GeneratePhotoRequest request, AuthPrincipal principal) {
        return ProductImageResponse.from(
                photoService.generate(id, imageId, caller(principal), request.style(), request.wish()));
    }

    @PostMapping("/products/{id}/images/{imageId}/publish")
    public ProductImageResponse publish(@PathVariable UUID id, @PathVariable UUID imageId, AuthPrincipal principal) {
        return ProductImageResponse.from(photoService.setPublished(id, imageId, caller(principal), true));
    }

    @PostMapping("/products/{id}/images/{imageId}/unpublish")
    public ProductImageResponse unpublish(@PathVariable UUID id, @PathVariable UUID imageId, AuthPrincipal principal) {
        return ProductImageResponse.from(photoService.setPublished(id, imageId, caller(principal), false));
    }

    /** Сгенерировать вариант ИИ заново (например, после правки описания товара). */
    @PostMapping("/products/{id}/images/{imageId}/regenerate")
    public ProductImageResponse regenerate(@PathVariable UUID id, @PathVariable UUID imageId, AuthPrincipal principal) {
        return ProductImageResponse.from(photoService.regenerate(id, imageId, caller(principal)));
    }

    @PostMapping("/shops/{shopId}/products")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse create(@PathVariable UUID shopId, @Valid @RequestBody ProductCreateRequest request,
                             AuthPrincipal principal) {
        ListingTextService.Result text = listingText.clean(request.name(), request.description());
        UUID id = productService.create(
                shopId, caller(principal), request.categoryId(), text.name(), text.description(),
                request.unit(), request.costPriceMinor(), request.currency());
        return new IdResponse(id);
    }

    /** Вид товара для владельца/админа — включает {@code costPriceMinor} (в отличие от витрины). */
    @GetMapping("/products/{id}")
    public ProductResponse get(@PathVariable UUID id, AuthPrincipal principal) {
        Product product = productService.get(id);
        Authz.requireSelfOrAdmin(principal, product.getShop().getOwnerUserId());
        return ProductResponse.from(product);
    }

    @PutMapping("/products/{id}")
    public void update(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request,
                       AuthPrincipal principal) {
        ListingTextService.Result text = listingText.clean(request.name(), request.description());
        productService.update(
                id, caller(principal), request.categoryId(), text.name(), text.description(),
                request.unit(), request.costPriceMinor(), request.currency());
    }

    @PostMapping("/products/{id}/submit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@PathVariable UUID id, AuthPrincipal principal) {
        productService.submitForModeration(id, caller(principal));
    }

    @PostMapping("/products/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id, AuthPrincipal principal) {
        productService.archive(id, caller(principal));
    }

    @PatchMapping("/products/{id}/stock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setStock(@PathVariable UUID id, @Valid @RequestBody StockSetRequest request,
                         AuthPrincipal principal) {
        productService.setStock(id, caller(principal), request.quantity());
    }

    @PostMapping("/products/{id}/images")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse addImage(@PathVariable UUID id, @Valid @RequestBody ProductImageAddRequest request,
                               AuthPrincipal principal) {
        UUID imageId = productService.addImage(id, caller(principal), request.url());
        return new IdResponse(imageId);
    }

    @GetMapping("/products/{id}/images")
    public List<ProductImageResponse> listImages(@PathVariable UUID id) {
        return productService.listImages(id).stream().map(ProductImageResponse::from).toList();
    }

    @DeleteMapping("/products/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeImage(@PathVariable UUID id, @PathVariable UUID imageId, AuthPrincipal principal) {
        productService.removeImage(id, imageId, caller(principal));
    }

    /** {@code null} — админ, проверка владения не нужна; иначе id владельца из токена. */
    private static UUID caller(AuthPrincipal principal) {
        Authz.require(principal);
        return Authz.isAdmin(principal) ? null : principal.userId();
    }
}
