package si.trevol;

import java.util.List;
import java.util.Map;

/** permission_denied. */
public class PermissionDeniedException extends TrevolException {
    private static final long serialVersionUID = 1L;

    public PermissionDeniedException(String message, Integer status, String code, String type, String title, String detail, String requestId,
            Boolean retryable, Double retryAfter, List<FieldError> errors, String idempotencyKey,
            Map<String, Object> problem, Map<String, List<String>> headers, Throwable cause) {
        super(message, status, code, type, title, detail, requestId, retryable, retryAfter, errors, idempotencyKey, problem, headers, cause);
    }
}
