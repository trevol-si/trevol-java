package si.trevol;

import java.util.List;
import java.util.Map;

/** idempotency_key_reused. */
public class IdempotencyException extends TrevolException {
    private static final long serialVersionUID = 1L;

    public IdempotencyException(String message, Integer status, String code, String type, String title, String detail, String requestId,
            Boolean retryable, Double retryAfter, List<FieldError> errors, String idempotencyKey,
            Map<String, Object> problem, Map<String, List<String>> headers, Throwable cause) {
        super(message, status, code, type, title, detail, requestId, retryable, retryAfter, errors, idempotencyKey, problem, headers, cause);
    }
}
