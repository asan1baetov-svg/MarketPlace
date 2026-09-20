package greenecomall.notification.send;

import greenecomall.notification.config.NotificationProperties;
import greenecomall.notification.domain.Channel;
import greenecomall.notification.domain.Notification;
import greenecomall.notification.domain.NotificationTemplate;
import greenecomall.notification.domain.UserNotificationPref;
import greenecomall.notification.repo.NotificationRepository;
import greenecomall.notification.repo.NotificationTemplateRepository;
import greenecomall.notification.repo.UserNotificationPrefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationTemplateRepository templates;
    @Mock private NotificationRepository notifications;
    @Mock private UserNotificationPrefRepository prefs;
    @Mock private ChannelSender push;
    @Mock private ChannelSender sms;

    private NotificationService service;
    private final UUID user = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(push.channel()).thenReturn(Channel.PUSH);
        when(sms.channel()).thenReturn(Channel.SMS);
        service = new NotificationService(templates, notifications, prefs, List.of(push, sms),
                new NotificationProperties("ru", List.of(Channel.PUSH)), JsonMapper.builder().build(),
                Clock.systemUTC());
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void usesDefaultChannelAndRendersTemplate() {
        when(prefs.findByUserId(user)).thenReturn(List.of());
        when(templates.findByCodeAndChannelAndLocale("ORDER_CREATED", Channel.PUSH, "ru"))
                .thenReturn(Optional.of(new NotificationTemplate("ORDER_CREATED", Channel.PUSH, "ru", "S", "Заказ {{orderId}}")));
        when(push.send(eq(user), anyString(), anyString())).thenReturn("ref-1");

        service.notify(user, "ORDER_CREATED", Map.of("orderId", "42"), null);

        verify(push).send(user, "S", "Заказ 42");
        verify(sms, never()).send(any(), any(), any());
    }

    @Test
    void respectsUserPreferences() {
        when(prefs.findByUserId(user)).thenReturn(List.of(
                new UserNotificationPref(user, Channel.PUSH, false),
                new UserNotificationPref(user, Channel.SMS, true)));
        when(templates.findByCodeAndChannelAndLocale(anyString(), eq(Channel.SMS), eq("ru")))
                .thenReturn(Optional.of(new NotificationTemplate("X", Channel.SMS, "ru", null, "hi")));

        service.notify(user, "X", Map.of(), null);

        verify(sms).send(user, null, "hi");
        verify(push, never()).send(any(), any(), any());
    }

    @Test
    void missingTemplateIsLoggedAsSkipped() {
        when(prefs.findByUserId(user)).thenReturn(List.of());
        when(templates.findByCodeAndChannelAndLocale(anyString(), any(), anyString())).thenReturn(Optional.empty());
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);

        service.notify(user, "UNKNOWN", Map.of(), null);

        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(Notification.Status.SKIPPED);
    }

    @Test
    void senderFailureIsRecordedNotThrown() {
        when(templates.findByCodeAndChannelAndLocale(anyString(), eq(Channel.PUSH), anyString()))
                .thenReturn(Optional.of(new NotificationTemplate("X", Channel.PUSH, "ru", null, "t")));
        when(push.send(any(), any(), any())).thenThrow(new IllegalStateException("provider down"));
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);

        service.notify(user, "X", Map.of(), Channel.PUSH);

        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(Notification.Status.FAILED);
        assertThat(saved.getValue().getError()).contains("provider down");
    }
}
