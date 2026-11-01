package si.trevol;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * La base de todos los errores del SDK (docs/api/43 §2.6.5): un {@code catch (TrevolException e)} ya lo atrapa todo.
 *
 * <p>Es una {@link RuntimeException}: toda respuesta que no es 2xx llega como una subclase, según el {@code code} del problem+json (el mismo
 * vocabulario que https://platform.trevol.si/docs/errores). Ninguna lleva la llave ni las cabeceras de la PETICIÓN.
 *
 * <p>Usa los métodos síncronos ({@code execute()}); en las llamadas asíncronas de OkHttp ({@code executeAsync}) una excepción del interceptor
 * llega como un {@code ApiException} que envuelve un {@code IOException} («canceled due to …»).
 */
public class TrevolException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Una de las {@code errors[]} de un 422: qué campo falló ({@code pointer}) y por qué. */
    public static final class FieldError {
        private final String field;
        private final String pointer;
        private final String message;
        private final Map<String, Object> extra;

        public FieldError(String field, String pointer, String message, Map<String, Object> extra) {
            this.field = field;
            this.pointer = pointer;
            this.message = message;
            this.extra = extra == null ? Collections.emptyMap() : extra;
        }

        public String getField() { return field; }
        public String getPointer() { return pointer; }
        public String getMessage() { return message; }
        /** Los campos que el SDK no conoce todavía. */
        public Map<String, Object> getExtra() { return extra; }

        @Override
        public String toString() { return pointer + " " + message; }
    }

    private final Integer status;
    private final String code;
    private final String type;
    private final String title;
    private final String detail;
    private final String requestId;
    private final Boolean retryable;
    private final Double retryAfter;
    private final transient List<FieldError> errors;
    private final String idempotencyKey;
    private final transient Map<String, Object> problem;
    private final transient Map<String, List<String>> headers;

    /** Para los errores locales y de red (sin problem+json). */
    protected TrevolException(String message, String idempotencyKey, Throwable cause) {
        this(message, null, null, null, null, null, null, null, null, null, idempotencyKey, null, null, cause);
    }

    public TrevolException(String message, Integer status, String code, String type, String title, String detail, String requestId,
                           Boolean retryable, Double retryAfter, List<FieldError> errors, String idempotencyKey,
                           Map<String, Object> problem, Map<String, List<String>> headers, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.type = type;
        this.title = title;
        this.detail = detail;
        this.requestId = requestId;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
        this.errors = errors == null ? Collections.emptyList() : errors;
        this.idempotencyKey = idempotencyKey;
        this.problem = problem;
        this.headers = headers == null ? Collections.emptyMap() : headers;
    }

    /** El estado HTTP (null si no hubo respuesta). */
    public Integer getStatus() { return status; }
    /** El {@code code} del problem+json. */
    public String getCode() { return code; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public String getRequestId() { return requestId; }
    public Boolean getRetryable() { return retryable; }
    /** Segundos que pidió la API esperar (cabecera {@code Retry-After} o {@code retry_after} del cuerpo). */
    public Double getRetryAfter() { return retryAfter; }
    /** Las {@code errors[]} de un 422. */
    public List<FieldError> getErrors() { return errors; }
    /** La Idempotency-Key de la llamada: úsala para reintentar más tarde con la MISMA clave. */
    public String getIdempotencyKey() { return idempotencyKey; }
    /** El cuerpo problem+json completo, con los campos extra ({@code existing_id}, {@code reason}, {@code document}, {@code rules}…). */
    public Map<String, Object> getProblem() { return problem; }
    /** Las cabeceras de la RESPUESTA. */
    public Map<String, List<String>> getHeaders() { return headers; }

    /** Clase, code, estado, detalle y request_id; nunca la llave ni cabeceras de la petición. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        if (code != null) sb.append(' ').append(code);
        if (status != null) sb.append(" HTTP ").append(status);
        sb.append(": ").append(detail != null ? detail : title != null ? title : getMessage());
        if (requestId != null) sb.append(" [request_id=").append(requestId).append(']');
        return sb.toString();
    }
}
