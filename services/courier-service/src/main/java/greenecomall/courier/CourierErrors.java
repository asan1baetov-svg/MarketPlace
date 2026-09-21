package greenecomall.courier;

/** Коды доменных ошибок courier-service. */
public final class CourierErrors {

    public static final String COURIER_NOT_FOUND = "courier.courier_not_found";
    public static final String COURIER_ALREADY_REGISTERED = "courier.courier_already_registered";
    public static final String COURIER_NOT_APPROVED = "courier.courier_not_approved";
    public static final String MODERATION_STATUS_INVALID = "courier.moderation_status_invalid";

    public static final String JOB_NOT_FOUND = "courier.job_not_found";
    public static final String JOB_STATUS_INVALID = "courier.job_status_invalid";
    public static final String JOB_FORBIDDEN = "courier.job_forbidden";
    public static final String JOB_NOT_READY_FOR_PICKUP = "courier.job_not_ready_for_pickup";
    public static final String ORDER_UNAVAILABLE = "courier.order_unavailable";

    private CourierErrors() {
    }
}
