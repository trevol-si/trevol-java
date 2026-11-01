package si.trevol;

import java.util.List;
import java.util.Map;

/** internal_error y cualquier 5xx sin code conocido. */
public class InternalServerException extends TrevolException {
    private static final long serialVersionUID = 1L;

    public InternalServerException(String message, Integer status, String code, String type, String title, String detail, String requestId,
            Boolean retryable, Double retryAfter, List<FieldError> errors, String idempotencyKey,
            Map<String, Object> problem, Map<String, List<String>> headers, Throwable cause) {
        super(message, status, code, type, title, detail, requestId, retryable, retryAfter, errors, idempotencyKey, problem, headers, cause);
    }
}
