package si.trevol;

import java.util.List;
import java.util.Map;

/** method_not_allowed, unsupported_version, invalid_json, invalid_request, idempotency_key_required. */
public class InvalidRequestException extends TrevolException {
    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message, Integer status, String code, String type, String title, String detail, String requestId,
            Boolean retryable, Double retryAfter, List<FieldError> errors, String idempotencyKey,
            Map<String, Object> problem, Map<String, List<String>> headers, Throwable cause) {
        super(message, status, code, type, title, detail, requestId, retryable, retryAfter, errors, idempotencyKey, problem, headers, cause);
    }
}
