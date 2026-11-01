package si.trevol;

/** La firma, la fecha o las cabeceras de un webhook no son válidas. {@link #getReason()}: «firma», «fecha», «cabeceras» o «cuerpo». */
public class WebhookVerificationException extends TrevolException {
    private static final long serialVersionUID = 1L;
    private final String reason;

    public WebhookVerificationException(String message, String reason) {
        super(message, null, null);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
