package si.trevol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import si.trevol.generado.Tablas;

/** Convierte una respuesta que no es 2xx en su excepción tipada (docs/api/43 §2.6.5). */
final class Errores {
    private Errores() {}

    private static final int RECORTE = 2048;
    private static final Gson GSON = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();
    private static final Type MAPA = new TypeToken<Map<String, Object>>() {}.getType();

    private static String cadena(Map<String, Object> m, String clave) {
        return m != null && m.get(clave) instanceof String ? (String) m.get(clave) : null;
    }

    static Double numero(Object v) {
        Double n = null;
        if (v instanceof Number) {
            n = ((Number) v).doubleValue();
        } else if (v instanceof String) {
            try {
                n = Double.parseDouble(((String) v).trim());
            } catch (NumberFormatException e) {
                n = null;
            }
        }
        return n != null && !n.isNaN() && !n.isInfinite() && n >= 0 ? n : null;
    }

    /** El valor de {@code Retry-After}: segundos o una fecha HTTP. null si no está o no se entiende. */
    static Double leerRetryAfter(String valor, Instant ahora) {
        if (valor == null || valor.trim().isEmpty()) return null;
        Double n = numero(valor);
        if (n != null) return n;
        try {
            Instant fecha = java.time.ZonedDateTime.parse(valor.trim(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return (double) Math.max(0, (long) Math.ceil((fecha.toEpochMilli() - ahora.toEpochMilli()) / 1000.0));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static TrevolException deRespuesta(int estado, String texto, Map<String, List<String>> cabeceras, String idempotencyKey, Instant ahora) {
        Map<String, String> cab = new LinkedHashMap<>();
        cabeceras.forEach((k, v) -> cab.put(k.toLowerCase(Locale.ROOT), String.join(", ", v)));
        Map<String, Object> cuerpo = null;
        try {
            cuerpo = GSON.fromJson(texto, MAPA);
        } catch (RuntimeException e) {
            cuerpo = null;
        }
        String code = cadena(cuerpo, "code");
        String detail = cadena(cuerpo, "detail");
        String title = cadena(cuerpo, "title");
        List<TrevolException.FieldError> errores = new ArrayList<>();
        if (cuerpo != null && cuerpo.get("errors") instanceof List) {
            for (Object x : (List<Object>) cuerpo.get("errors")) {
                if (x instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) x;
                    Map<String, Object> extra = new LinkedHashMap<>(m);
                    extra.remove("field");
                    extra.remove("pointer");
                    extra.remove("message");
                    errores.add(new TrevolException.FieldError(cadena(m, "field"), cadena(m, "pointer"), cadena(m, "message"), extra));
                }
            }
        }
        Boolean retryable = cuerpo != null && cuerpo.get("retryable") instanceof Boolean ? (Boolean) cuerpo.get("retryable") : null;
        Double retryAfter = leerRetryAfter(cab.get("retry-after"), ahora);
        if (retryAfter == null && cuerpo != null) retryAfter = numero(cuerpo.get("retry_after"));
        String mensaje = detail != null ? detail : title != null ? title
                : cuerpo != null ? "La API respondió " + estado : "La API respondió " + estado + " sin problem+json";
        String nombre;
        if (code != null) {
            nombre = Tablas.CLASE_POR_CODE.get(code); // un code que el SDK no conoce (la API sumó uno) da la base, nunca rompe
        } else {
            nombre = Tablas.CLASE_POR_ESTADO.get(Integer.toString(estado));
            if (nombre == null && estado >= 500) nombre = Tablas.CLASE_POR_ESTADO.get("5xx");
        }
        String detalle = detail;
        if (cuerpo == null) {
            String t = texto == null ? "" : texto.trim();
            detalle = t.isEmpty() ? null : t.substring(0, Math.min(t.length(), RECORTE));
        }
        String requestId = cadena(cuerpo, "request_id");
        if (requestId == null) requestId = cab.get("request-id");
        String type = cadena(cuerpo, "type");
        return crear(nombre, mensaje, estado, code, type, title, detalle, requestId, retryable, retryAfter, errores, idempotencyKey, cuerpo, cabeceras);
    }

    private static TrevolException crear(String nombre, String m, int s, String code, String type, String title, String detail, String rid,
                                         Boolean retryable, Double ra, List<TrevolException.FieldError> er, String clave,
                                         Map<String, Object> problema, Map<String, List<String>> cab) {
        switch (nombre == null ? "" : nombre) {
            case "AuthenticationError": return new AuthenticationException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
            case "PermissionDeniedError": return new PermissionDeniedException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
            case "NotFoundError": return new NotFoundException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
            case "InvalidRequestError": return new InvalidRequestException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
            case "IdempotencyError": return new IdempotencyException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
            case "ValidationError": return new ValidationException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
            case "ConflictError": return new ConflictException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
            case "PreconditionError": return new PreconditionException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
            case "DianRejectedError": return new DianRejectedException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
            case "BalanceError": return new BalanceException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
            case "RateLimitError": return new RateLimitException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
            case "DianUnavailableError": return new DianUnavailableException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
            case "InternalServerError": return new InternalServerException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
            default: return new TrevolException(m, s, code, type, title, detail, rid, retryable, ra, er, clave, problema, cab, null);
        }
    }
}
