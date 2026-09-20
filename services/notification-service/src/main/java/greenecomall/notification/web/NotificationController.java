package greenecomall.notification.web;

import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.web.PageResponse;
import greenecomall.common.web.security.Authz;
import greenecomall.notification.domain.Channel;
import greenecomall.notification.domain.DeviceToken;
import greenecomall.notification.domain.Notification;
import greenecomall.notification.domain.NotificationTemplate;
import greenecomall.notification.domain.UserNotificationPref;
import greenecomall.notification.repo.DeviceTokenRepository;
import greenecomall.notification.repo.NotificationRepository;
import greenecomall.notification.repo.NotificationTemplateRepository;
import greenecomall.notification.repo.UserNotificationPrefRepository;
import greenecomall.notification.send.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Настройки каналов, push-токены, история уведомлений, служебная отправка, шаблоны.
 * Пользователь — {@code sub} проверенного access-JWT; {@code /internal/**} — по {@code X-Internal-Token}.
 */
@RestController
public class NotificationController {

    private final NotificationService notificationService;
    private final UserNotificationPrefRepository prefs;
    private final DeviceTokenRepository devices;
    private final NotificationRepository notifications;
    private final NotificationTemplateRepository templates;

    public NotificationController(NotificationService notificationService, UserNotificationPrefRepository prefs,
                                  DeviceTokenRepository devices, NotificationRepository notifications,
                                  NotificationTemplateRepository templates) {
        this.notificationService = notificationService;
        this.prefs = prefs;
        this.devices = devices;
        this.notifications = notifications;
        this.templates = templates;
    }

    // ─── клиент ────────────────────────────────────────────────────────────────

    public record PreferencesDto(Map<Channel, Boolean> channels) {
    }

    @GetMapping("/notifications/preferences")
    public PreferencesDto preferences(AuthPrincipal principal) {
        UUID userId = Authz.require(principal).userId();
        java.util.EnumMap<Channel, Boolean> map = new java.util.EnumMap<>(Channel.class);
        List<Channel> effective = notificationService.effectiveChannels(userId);
        for (Channel c : Channel.values()) {
            map.put(c, effective.contains(c));
        }
        return new PreferencesDto(map);
    }

    @PutMapping("/notifications/preferences")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void updatePreferences(@RequestBody PreferencesDto request, AuthPrincipal principal) {
        UUID userId = Authz.require(principal).userId();
        request.channels().forEach((channel, enabled) -> prefs
                .findById(new UserNotificationPref.Key(userId, channel))
                .ifPresentOrElse(p -> p.setEnabled(enabled),
                        () -> prefs.save(new UserNotificationPref(userId, channel, enabled))));
    }

    public record DeviceRequest(@NotNull DeviceToken.Platform platform, @NotBlank String token) {
    }

    @PostMapping("/notifications/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void registerDevice(@Valid @RequestBody DeviceRequest request, AuthPrincipal principal) {
        UUID userId = Authz.require(principal).userId();
        if (devices.findByToken(request.token()).isEmpty()) {
            devices.save(new DeviceToken(userId, request.platform(), request.token()));
        }
    }

    public record NotificationDto(UUID id, String channel, String templateCode, String text, String status,
                                  Instant createdAt, Instant sentAt) {
        static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getChannel().name(), n.getTemplateCode(), n.getRenderedText(),
                    n.getStatus().name(), n.getCreatedAt(), n.getSentAt());
        }
    }

    @GetMapping("/notifications")
    public PageResponse<NotificationDto> history(Pageable pageable, AuthPrincipal principal) {
        Page<Notification> page = notifications.findByUserIdOrderByCreatedAtDesc(Authz.require(principal).userId(), pageable);
        return PageResponse.of(page.getContent().stream().map(NotificationDto::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    // ─── служебное ─────────────────────────────────────────────────────────────

    public record SendRequest(UUID userId, @NotBlank String templateCode, Channel channel, Map<String, Object> payload) {
    }

    @PostMapping("/internal/notifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void send(@Valid @RequestBody SendRequest request) {
        notificationService.notify(request.userId(), request.templateCode(), request.payload(), request.channel());
    }

    // ─── админ: шаблоны ────────────────────────────────────────────────────────

    public record TemplateDto(UUID id, @NotBlank String code, @NotNull Channel channel, @NotBlank String locale,
                              String subject, @NotBlank String body) {
        static TemplateDto from(NotificationTemplate t) {
            return new TemplateDto(t.getId(), t.getCode(), t.getChannel(), t.getLocale(), t.getSubject(), t.getBody());
        }
    }

    @GetMapping("/admin/notification-templates")
    public List<TemplateDto> templates() {
        return templates.findAll().stream().map(TemplateDto::from).toList();
    }

    @PutMapping("/admin/notification-templates")
    @Transactional
    public TemplateDto upsertTemplate(@Valid @RequestBody TemplateDto request) {
        NotificationTemplate t = templates
                .findByCodeAndChannelAndLocale(request.code(), request.channel(), request.locale())
                .map(existing -> {
                    existing.update(request.subject(), request.body());
                    return existing;
                })
                .orElseGet(() -> templates.save(new NotificationTemplate(
                        request.code(), request.channel(), request.locale(), request.subject(), request.body())));
        return TemplateDto.from(t);
    }

    @GetMapping("/admin/notifications")
    public PageResponse<NotificationDto> adminFeed(Pageable pageable) {
        Page<Notification> page = notifications.findByUserIdIsNullOrderByCreatedAtDesc(pageable);
        return PageResponse.of(page.getContent().stream().map(NotificationDto::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
