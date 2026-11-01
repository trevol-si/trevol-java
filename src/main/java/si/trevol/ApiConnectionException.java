package si.trevol;

/** No hubo respuesta: red, DNS, TLS o tiempo agotado. Lleva la Idempotency-Key para reintentar sin duplicar. */
public class ApiConnectionException extends TrevolException {
    private static final long serialVersionUID = 1L;

    public ApiConnectionException(String message, String idempotencyKey, Throwable cause) {
        super(message, idempotencyKey, cause);
    }
}
