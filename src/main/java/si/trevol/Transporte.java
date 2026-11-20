package si.trevol;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import si.trevol.generado.Tablas;

/**
 * El transporte: lo que hace que las llamadas se comporten igual en los 7 SDK (docs/api/43 §2.6.2 a §2.6.5).
 *
 * <p>Es un {@link Interceptor} de OkHttp (el punto de extensión del código generado): pone las cabeceras y la Idempotency-Key, reintenta y
 * convierte toda respuesta que no es 2xx en una {@link TrevolException} (una {@link RuntimeException}) que atraviesa el código generado sin
 * envolverse en {@code ApiException}.
 */
public final class Transporte implements Interceptor {
    private static final java.util.Set<Integer> REINTENTABLES = java.util.Set.of(429, 500, 502, 503, 504);
    private static final java.util.Set<String> EN_CURSO = java.util.Set.of("request_in_progress", "previous_event_pending");
    private static final String PROBLEMA = "application/problem+json";
    private static final Pattern CLAVE_VALIDA = Pattern.compile("^[!-~]{1,255}$");

    /** Qué pasó en un intento: una respuesta (con estado) o un fallo de red (sin respuesta). {@code retryAfter} en segundos, ya resuelto. */
    public static final class Resultado {
        final boolean red;
        final Integer estado;
        final String code;
        final Boolean retryable;
        final Double retryAfter;

        private Resultado(boolean red, Integer estado, String code, Boolean retryable, Double retryAfter) {
            this.red = red;
            this.estado = estado;
            this.code = code;
            this.retryable = retryable;
            this.retryAfter = retryAfter;
        }

        public static Resultado red() { return new Resultado(true, null, null, null, null); }

        public static Resultado respuesta(int estado, String code, Boolean retryable, Double retryAfter) {
            return new Resultado(false, estado, code, retryable, retryAfter);
        }
    }

    /** La decisión de {@link #decidir}: si se reintenta y cuántos segundos se espera. {@code motivo}: «exito», «agotado» o «retry_after_excede». */
    public static final class Decision {
        public final boolean reintentar;
        public final Double espera;
        public final String motivo;

        Decision(boolean reintentar, Double espera, String motivo) {
            this.reintentar = reintentar;
            this.espera = espera;
            this.motivo = motivo;
        }
    }

    /** La espera propia antes del intento siguiente: 0,5 · 2^(intento−1), con tope de 8 s, por un factor al azar en [0,75; 1]. */
    public static double esperaPropia(int intento, double azar) {
        return Math.min(0.5 * Math.pow(2, intento - 1), 8.0) * (0.75 + 0.25 * azar);
    }

    /** Función PURA: ¿se reintenta y cuánto se espera? Las mismas reglas y los mismos casos ({@code pruebas/datos/transporte.json}) en los 7 SDK. */
    public static Decision decidir(int intento, String reintento, int maxReintentos, double maxRetryAfter, double azar, Resultado r) {
        if (!r.red && r.estado >= 200 && r.estado < 300) return new Decision(false, null, "exito");
        boolean reintentable;
        if (r.red) {
            reintentable = "seguro".equals(reintento);
        } else if ("solo_429".equals(reintento)) {
            reintentable = r.estado == 429;
        } else if (r.estado == 402) {
            reintentable = false;
        } else {
            reintentable = REINTENTABLES.contains(r.estado) || (r.code != null && EN_CURSO.contains(r.code)) || Boolean.TRUE.equals(r.retryable);
        }
        if (!reintentable) return new Decision(false, null, null);
        if (intento > maxReintentos) return new Decision(false, null, "agotado");
        double propia = esperaPropia(intento, azar);
        double pedida = !r.red && r.retryAfter != null && r.retryAfter > 0 ? r.retryAfter : 0.0;
        if (pedida > maxRetryAfter) return new Decision(false, null, "retry_after_excede");
        return new Decision(true, Math.max(pedida, propia), null);
    }

    static void validarClave(String clave) {
        if (clave == null || !CLAVE_VALIDA.matcher(clave).matches()) {
            throw new ConfigurationException("La idempotencyKey debe tener de 1 a 255 caracteres ASCII visibles (sin espacios).");
        }
    }

    private static final Map<String, Tablas.Operacion> EXACTAS = new HashMap<>();
    private static final List<Map.Entry<Tablas.Operacion, Pattern>> PLANTILLAS = new java.util.ArrayList<>();

    static {
        for (Tablas.Operacion o : Tablas.OPERACIONES) {
            if (o.ruta.contains("{")) {
                PLANTILLAS.add(Map.entry(o, Pattern.compile("^" + Pattern.quote(o.ruta).replaceAll("\\{[a-z_]+\\}", "\\\\E[^/]+\\\\Q") + "$")));
            } else {
                EXACTAS.put(o.metodo + " " + o.ruta, o);
            }
        }
    }

    /** La operación (tablas) de un método y una ruta SIN el prefijo /v1. Las rutas fijas ganan a las plantillas. */
    static Tablas.Operacion operacion(String metodo, String ruta) {
        Tablas.Operacion o = EXACTAS.get(metodo + " " + ruta);
        if (o != null) return o;
        for (Map.Entry<Tablas.Operacion, Pattern> p : PLANTILLAS) {
            if (p.getKey().metodo.equals(metodo) && p.getValue().matcher(ruta).matches()) return p.getKey();
        }
        return null;
    }

    private static String aceptar(String binario) {
        if (binario == null) return "application/json, " + PROBLEMA;
        switch (binario) {
            case "pdf": return "application/pdf, " + PROBLEMA;
            case "xml": return "application/xml, " + PROBLEMA;
            case "zip": return "application/zip, " + PROBLEMA;
            case "png": return "image/png, " + PROBLEMA; // el logo de la empresa (0.4.0)
            case "csv": return "text/csv, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, " + PROBLEMA; // los reportes: CSV o Excel
            default: return "application/json, " + PROBLEMA;
        }
    }

    static final String CABECERA_EMPRESA = "Trevol-Company";
    static final String CABECERA_ENTORNO = "Trevol-Environment";
    static final String SIN_EMPRESA = "Con una llave de administración (tva_…), cada petición a una empresa necesita su id: pásalo al crear el cliente "
            + "(Trevol.builder().company(\"com_…\")) o usa trevol.withCompany(\"com_…\"). Las operaciones de trevol.account() no lo necesitan.";
    static final String SOLO_ADMINISTRACION = "Las operaciones de trevol.account() piden una llave de administración (tva_test_… o tva_live_…); "
            + "una llave tvl_ es de una sola empresa. La crea el titular de la cuenta en https://platform.trevol.si/administracion.";

    private final String llave;
    private final boolean administracion;
    private final String entorno;
    private final String empresa;
    private final String apiVersion;
    private final String userAgent;
    private final int maxReintentos;
    private final double maxRetryAfter;
    private final String rutaBase;
    private final DoubleConsumer dormir;
    private final DoubleSupplier azar;
    private final Supplier<String> uuid;
    private final Supplier<Instant> ahora;

    Transporte(String llave, String apiVersion, String userAgent, int maxReintentos, double maxRetryAfter, String rutaBase,
               DoubleConsumer dormir, DoubleSupplier azar, Supplier<String> uuid, Supplier<Instant> ahora) {
        this(llave, Llave.leer(llave).tipo == Trevol.KeyType.ACCOUNT, Llave.entorno(llave).toString(), null, apiVersion, userAgent, maxReintentos,
                maxRetryAfter, rutaBase, dormir, azar, uuid, ahora);
    }

    private Transporte(String llave, boolean administracion, String entorno, String empresa, String apiVersion, String userAgent, int maxReintentos,
                       double maxRetryAfter, String rutaBase, DoubleConsumer dormir, DoubleSupplier azar, Supplier<String> uuid, Supplier<Instant> ahora) {
        this.llave = llave;
        this.administracion = administracion;
        this.entorno = entorno;
        this.empresa = empresa;
        this.apiVersion = apiVersion;
        this.userAgent = userAgent;
        this.maxReintentos = maxReintentos;
        this.maxRetryAfter = maxRetryAfter;
        this.rutaBase = rutaBase;
        this.dormir = dormir;
        this.azar = azar;
        this.uuid = uuid;
        this.ahora = ahora;
    }

    /** El mismo transporte (llave, reintentos, reloj) para otra empresa. {@code empresa} ya viene validada; solo con una llave tva_. */
    Transporte conEmpresa(String empresa) {
        return new Transporte(llave, administracion, entorno, empresa, apiVersion, userAgent, maxReintentos, maxRetryAfter, rutaBase, dormir, azar, uuid, ahora);
    }

    /** La llave nunca sale en un toString, un log ni un volcado. */
    @Override
    public String toString() {
        return "Transporte{apiVersion=" + apiVersion + ", maxReintentos=" + maxReintentos + (empresa != null ? ", company=" + empresa : "") + "}";
    }

    /**
     * Qué llave admite una ruta: la de la tabla de operaciones y, si la ruta no está en la tabla (una operación más nueva que el SDK),
     * {@code /account…} es solo de la llave de administración, {@code /health} no lleva llave y todo lo demás es de una empresa.
     */
    static String llaveDe(Tablas.Operacion op, String ruta) {
        if (op != null && op.llave != null) return op.llave;
        if (ruta.equals("/account") || ruta.startsWith("/account/")) return "administracion";
        if (ruta.equals("/health")) return "ninguna";
        return "api_o_administracion";
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        String metodo = original.method();
        String ruta = original.url().encodedPath();
        if (!rutaBase.isEmpty() && ruta.startsWith(rutaBase)) ruta = ruta.substring(rutaBase.length());
        Tablas.Operacion op = operacion(metodo, ruta);
        String reintento = op != null ? op.reintento : "GET".equals(metodo) ? "seguro" : "solo_429";

        // Antes de la red: una operación de la cuenta con una tvl_ la API la rechaza de seguro (401), así que no sale.
        String llaveOp = llaveDe(op, ruta);
        if (!administracion && "administracion".equals(llaveOp)) throw new ConfigurationException(SOLO_ADMINISTRACION);

        // La empresa y el entorno: solo con una llave de administración y solo en las rutas de una empresa. Con una tvl_ no viajan nunca
        // (su empresa y su entorno salen de la llave), ni aunque otro interceptor los haya puesto.
        Request.Builder b = original.newBuilder().removeHeader(CABECERA_EMPRESA).removeHeader(CABECERA_ENTORNO);
        if (administracion && "api_o_administracion".equals(llaveOp)) {
            if (empresa == null) throw new ConfigurationException(SIN_EMPRESA);
            b.header(CABECERA_EMPRESA, empresa).header(CABECERA_ENTORNO, entorno);
        }

        // La Idempotency-Key: una por llamada, la misma en todos los reintentos.
        String clave = null;
        if (op != null && op.idempotente) {
            String dada = original.header("Idempotency-Key");
            if (dada == null) {
                clave = uuid.get();
            } else {
                validarClave(dada);
                clave = dada;
            }
            b.header("Idempotency-Key", clave);
        } else {
            b.removeHeader("Idempotency-Key");
        }
        b.header("Authorization", "Bearer " + llave)
                .header("Trevol-Version", apiVersion)
                .header("User-Agent", userAgent)
                .header("Accept", aceptar(op != null ? op.binario : null));
        Request peticion = b.build();

        for (int intento = 1; ; intento++) {
            Response respuesta = null;
            IOException causa = null;
            try {
                respuesta = chain.proceed(peticion);
            } catch (IOException e) {
                if (chain.call().isCanceled()) throw e; // la persona canceló: no es un fallo de red
                causa = e;
            }
            Decision decision;
            if (respuesta != null) {
                int estado = respuesta.code();
                if (respuesta.isSuccessful()) return respuesta;
                ResponseBody cuerpo = respuesta.body();
                String texto = cuerpo == null ? "" : cuerpo.string();
                Map<String, List<String>> cabeceras = new LinkedHashMap<>(respuesta.headers().toMultimap());
                respuesta.close();
                TrevolException error = Errores.deRespuesta(estado, texto, cabeceras, clave, ahora.get());
                decision = decidir(intento, reintento, maxReintentos, maxRetryAfter, azar.getAsDouble(),
                        Resultado.respuesta(estado, error.getCode(), error.getRetryable(), error.getRetryAfter()));
                if (!decision.reintentar) throw error;
            } else {
                decision = decidir(intento, reintento, maxReintentos, maxRetryAfter, azar.getAsDouble(), Resultado.red());
                if (!decision.reintentar) {
                    String detalle = causa.getMessage() == null ? causa.getClass().getSimpleName() : causa.getMessage();
                    throw new ApiConnectionException("No pude conectar con la API de Trevol (" + detalle.substring(0, Math.min(detalle.length(), 200)) + ").", clave, causa);
                }
            }
            dormir.accept(decision.espera == null ? 0.0 : decision.espera);
        }
    }
}
