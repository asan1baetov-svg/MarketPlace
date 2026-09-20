package greenecomall.mlm.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Ежечасно: просрочка окон активации и напоминания о дедлайне (docs/ARCHITECTURE.md §2.6). */
@Component
public class MlmScheduler {

    private static final Logger log = LoggerFactory.getLogger(MlmScheduler.class);

    private final AccountService accountService;

    public MlmScheduler(AccountService accountService) {
        this.accountService = accountService;
    }

    @Scheduled(fixedDelayString = "${mlm.deadline-check-interval:1h}", initialDelayString = "${mlm.deadline-check-initial-delay:30s}")
    public void run() {
        int expired = accountService.expireOverdue();
        int reminded = accountService.sendDeadlineReminders();
        if (expired + reminded > 0) {
            log.info("mlm scheduler: expired={}, reminders={}", expired, reminded);
        }
    }
}
