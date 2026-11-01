package si.trevol;

import java.util.List;
import java.util.Map;

/** insufficient_balance, top_up_below_minimum, top_up_above_maximum, top_ups_unavailable. */
public class BalanceException extends TrevolException {
    private static final long serialVersionUID = 1L;

    public BalanceException(String message, Integer status, String code, String type, String title, String detail, String requestId,
            Boolean retryable, Double retryAfter, List<FieldError> errors, String idempotencyKey,
            Map<String, Object> problem, Map<String, List<String>> headers, Throwable cause) {
        super(message, status, code, type, title, detail, requestId, retryable, retryAfter, errors, idempotencyKey, problem, headers, cause);
    }
}
