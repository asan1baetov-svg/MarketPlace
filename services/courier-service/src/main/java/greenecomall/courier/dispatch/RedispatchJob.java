package greenecomall.courier.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Периодически передаёт истёкшие офферы следующему кандидату и повторяет подбор для задач без курьера. */
@Component
public class RedispatchJob {

    private static final Logger log = LoggerFactory.getLogger(RedispatchJob.class);

    private final DispatchService dispatchService;

    public RedispatchJob(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${courier.redispatch-interval:30s}")
    public void run() {
        int touched = dispatchService.redispatchStale();
        if (touched > 0) {
            log.debug("redispatch: re-evaluated {} delivery job(s)", touched);
        }
    }
}
