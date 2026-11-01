package si.trevol;

import com.standardwebhooks.exceptions.EmptyWebhookSecretException;
import com.standardwebhooks.exceptions.WebhookSigningException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import si.trevol.generado.JSON;
import si.trevol.generado.Tablas;
import si.trevol.generado.model.Event;

/**
 * Verificación de webhooks (docs/api/43 §2.6.9): Standard Webhooks sobre el CUERPO CRUDO.
 *
 * <p>La firma esperada la calcula la biblioteca abierta {@code com.standardwebhooks:standardwebhooks} ({@code Webhook.sign}); la tolerancia de
 * 5 minutos (con reloj inyectable en las pruebas), las cabeceras (en cualquier caja) y la comparación en tiempo constante son de este SDK: la
 * biblioteca no deja inyectar el reloj y entrega solo «pasó o no pasó».
 */
public final class Webhook {
    /** Tolerancia de {@code webhook-timestamp}, en segundos, hacia atrás y hacia adelante. */
    public static final long TOLERANCIA = 300;
    private static final Pattern DIGITOS = Pattern.compile("^[0-9]{1,12}$");

    private final com.standardwebhooks.Webhook firmador;
    private final LongSupplier ahora;

    /** @param secret el secreto del endpoint ({@code whsec_…}), que se muestra una sola vez al crearlo o rotarlo */
    public Webhook(String secret) {
        this(secret, () -> Instant.now().getEpochSecond());
    }

    /** Solo para pruebas: con un reloj propio (segundos). */
    Webhook(String secret, LongSupplier ahora) {
        String s = secret == null ? "" : secret.trim();
        if (s.isEmpty() || s.equals("whsec_")) {
            throw new ConfigurationException("Falta el secreto del webhook (whsec_…). Se muestra una sola vez al crear o rotar el endpoint.");
        }
        try {
            this.firmador = new com.standardwebhooks.Webhook(s);
        } catch (IllegalArgumentException | EmptyWebhookSecretException e) { // base64 inválido o vacío: nunca se cuenta el secreto
            throw new ConfigurationException("El secreto del webhook no tiene el formato esperado (whsec_ + base64).");
        }
        this.ahora = ahora;
    }

    /** Las tres cabeceras en minúsculas, venga lo que venga. La primera repetida gana. */
    static Map<String, String> normalizar(Map<String, ? extends Object> cabeceras) {
        Map<String, String> salida = new HashMap<>();
        for (Map.Entry<String, ? extends Object> e : cabeceras.entrySet()) {
            String nombre = e.getKey().toLowerCase(java.util.Locale.ROOT);
            Object v = e.getValue();
            if (v instanceof List) v = ((List<?>) v).isEmpty() ? null : ((List<?>) v).get(0);
            if (v != null && (nombre.equals("webhook-id") || nombre.equals("webhook-timestamp") || nombre.equals("webhook-signature"))) {
                salida.putIfAbsent(nombre, v.toString());
            }
        }
        return salida;
    }

    /** Verifica el cuerpo crudo en bytes (lo normal: {@code request.getInputStream().readAllBytes()}). */
    public Event verify(byte[] body, Map<String, ? extends Object> headers) {
        try {
            String texto = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
            return verify(texto, headers);
        } catch (CharacterCodingException e) {
            throw new WebhookVerificationException("El cuerpo del webhook no es UTF-8.", "cuerpo");
        }
    }

    /**
     * Verifica la firma y la fecha de una entrega y devuelve el evento. Lanza {@link WebhookVerificationException} si algo no cuadra.
     *
     * <p>Ojo: la verificación NO evita repeticiones dentro de los 5 minutos ni los reintentos de entrega: deduplica por {@code webhook-id}.
     *
     * @param body el CUERPO CRUDO tal como llegó (la firma se calcula sobre los bytes exactos)
     * @param headers las cabeceras: {@code Map<String, List<String>>} (Servlet, {@code HttpHeaders}) o {@code Map<String, String>}, en cualquier caja
     */
    public Event verify(String body, Map<String, ? extends Object> headers) {
        if (body == null) {
            throw new WebhookVerificationException("Falta el cuerpo del webhook: debe ser el CUERPO CRUDO, no un objeto ya parseado "
                    + "(en Spring: @RequestBody String; en Servlet: request.getInputStream().readAllBytes()).", "cuerpo");
        }
        Map<String, String> h = normalizar(headers);
        for (String n : new String[] {"webhook-id", "webhook-timestamp", "webhook-signature"}) {
            if (h.getOrDefault(n, "").isEmpty()) throw new WebhookVerificationException("Falta la cabecera " + n + ".", "cabeceras");
        }
        if (!DIGITOS.matcher(h.get("webhook-timestamp")).matches()) {
            throw new WebhookVerificationException("El webhook-timestamp no es un número de segundos.", "fecha");
        }
        long marca = Long.parseLong(h.get("webhook-timestamp"));
        if (Math.abs(ahora.getAsLong() - marca) > TOLERANCIA) {
            throw new WebhookVerificationException("La fecha del webhook está fuera de la tolerancia de 5 minutos.", "fecha");
        }
        String esperada;
        try {
            String firmada = firmador.sign(h.get("webhook-id"), marca, body);
            esperada = firmada.substring(firmada.indexOf(',') + 1);
        } catch (WebhookSigningException e) {
            throw new WebhookVerificationException("Las cabeceras del webhook no son válidas.", "cabeceras");
        }
        boolean coincide = false;
        for (String firma : h.get("webhook-signature").split(" ")) {
            String[] partes = firma.split(",", 2);
            if (partes.length == 2 && partes[0].equals("v1")
                    && MessageDigest.isEqual(esperada.getBytes(StandardCharsets.UTF_8), partes[1].getBytes(StandardCharsets.UTF_8))) {
                coincide = true; // basta una válida (rotación del secreto); se recorren todas
            }
        }
        if (!coincide) {
            throw new WebhookVerificationException("La firma del webhook no coincide: revisa el secreto y que el cuerpo sea el crudo, sin reescribir.", "firma");
        }
        try {
            return Event.fromJson(body);
        } catch (Exception e) {
            throw new WebhookVerificationException("El cuerpo firmado no es un evento válido.", "cuerpo");
        }
    }

    /**
     * El {@code data} del evento como el modelo de su {@code type} (p. ej. {@code DocumentEventData} para {@code invoice.accepted}). null si el
     * tipo es nuevo y este SDK todavía no lo conoce: el evento no se pierde, {@code evento.getData()} sigue ahí como mapa.
     */
    public Object eventData(Event evento) {
        Class<?> modelo = Tablas.EVENTO_MODELOS.get(evento.getType());
        if (modelo == null || evento.getData() == null) return null;
        return JSON.getGson().fromJson(JSON.getGson().toJsonTree(evento.getData()), modelo);
    }
}
