package greenecomall.order.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SuborderStatusTest {

    @Test
    void happyPathTransitionsAreAllowed() {
        assertThat(SuborderStatus.CREATED.canTransitionTo(SuborderStatus.PAID)).isTrue();
        assertThat(SuborderStatus.PAID.canTransitionTo(SuborderStatus.ACCEPTED)).isTrue();
        assertThat(SuborderStatus.ACCEPTED.canTransitionTo(SuborderStatus.ASSEMBLED)).isTrue();
        assertThat(SuborderStatus.ASSEMBLED.canTransitionTo(SuborderStatus.HANDED_TO_COURIER)).isTrue();
        assertThat(SuborderStatus.HANDED_TO_COURIER.canTransitionTo(SuborderStatus.IN_TRANSIT)).isTrue();
        assertThat(SuborderStatus.IN_TRANSIT.canTransitionTo(SuborderStatus.DELIVERED)).isTrue();
        assertThat(SuborderStatus.DELIVERED.canTransitionTo(SuborderStatus.COMPLETED)).isTrue();
    }

    @Test
    void skippingStagesIsRejected() {
        assertThat(SuborderStatus.CREATED.canTransitionTo(SuborderStatus.ACCEPTED)).isFalse();
        assertThat(SuborderStatus.PAID.canTransitionTo(SuborderStatus.ASSEMBLED)).isFalse();
        assertThat(SuborderStatus.DELIVERED.canTransitionTo(SuborderStatus.IN_TRANSIT)).isFalse();
    }

    @Test
    void terminalStatusesGoNowhere() {
        for (SuborderStatus terminal : new SuborderStatus[]{
                SuborderStatus.COMPLETED, SuborderStatus.CANCELLED, SuborderStatus.REFUNDED}) {
            assertThat(terminal.isTerminal()).isTrue();
            for (SuborderStatus target : SuborderStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).isFalse();
            }
        }
    }

    @Test
    void cancelIsReachableFromEveryOpenStage() {
        for (SuborderStatus status : SuborderStatus.values()) {
            if (!status.isTerminal() && status != SuborderStatus.DELIVERED) {
                assertThat(status.canTransitionTo(SuborderStatus.CANCELLED))
                        .as("cancel from %s", status)
                        .isTrue();
            }
        }
    }
}
