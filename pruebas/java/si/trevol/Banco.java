package si.trevol;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import okhttp3.Headers;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;

/** Ayudas de las pruebas: un servidor HTTP falso en localhost (pasa por OkHttp de verdad) y un cliente con reloj y azar inyectados. */
final class Banco implements AutoCloseable {
    static final String LLAVE_TEST = "tvl_test_" + "a1".repeat(32);
    static final String LLAVE_LIVE = "tvl_live_" + "b2".repeat(32);
    static final String HEX = "3f0c1a2b".repeat(4);
    static final String ID_DOC = "inv_" + HEX;
    static final Gson GSON = new Gson();
    static final String DOCUMENTO = "{\"id\":\"" + ID_DOC + "\",\"object\":\"invoice\",\"environment\":\"test\",\"status\":\"accepted\",\"number\":\"SETP990012345\",\"external_id\":\"VENTA-2026-000123\"}";
    static final String ENTRADA_FACTURA = "{\"external_id\":\"VENTA-2026-000123\",\"customer\":{\"final_consumer\":true},\"items\":[{\"description\":\"Café\",\"quantity\":\"1\",\"unit_price\":\"1000\"}]}";

    /** Una respuesta del servidor falso. */
    static final class Resp {
        final int estado;
        final byte[] cuerpo;
        final String tipo;
        final Map<String, String> cabeceras;
        final boolean cortar;

        Resp(int estado, byte[] cuerpo, String tipo, Map<String, String> cabeceras, boolean cortar) {
            this.estado = estado;
            this.cuerpo = cuerpo;
            this.tipo = tipo;
            this.cabeceras = cabeceras;
            this.cortar = cortar;
        }
    }

    static Resp json(int estado, String cuerpo) { return new Resp(estado, cuerpo.getBytes(StandardCharsets.UTF_8), "application/json", Map.of(), false); }

    static Resp crudo(int estado, byte[] cuerpo, String tipo) { return new Resp(estado, cuerpo, tipo, Map.of(), false); }

    static Resp cortar() { return new Resp(0, new byte[0], "", Map.of(), true); }

    static Resp problema(int estado, String code, String extraJson, Map<String, String> cabeceras) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "https://platform.trevol.si/docs/errores#" + code);
        m.put("title", "Título");
        m.put("status", estado);
        m.put("code", code);
        m.put("retryable", false);
        m.put("detail", "Detalle de " + code);
        m.put("request_id", "req_c3c3c3c3c3c3c3c3c3c3c3c3");
        if (extraJson != null) m.putAll(GSON.<Map<String, Object>>fromJson(extraJson, new TypeToken<Map<String, Object>>() {}.getType()));
        return new Resp(estado, GSON.toJson(m).getBytes(StandardCharsets.UTF_8), "application/problem+json", cabeceras == null ? Map.of() : cabeceras, false);
    }

    static Resp problema(int estado, String code) { return problema(estado, code, null, null); }

    /** Una petición anotada. */
    static final class Pet {
        final String metodo;
        final String ruta;
        final Headers cabeceras;
        final String cuerpo;

        Pet(String metodo, String ruta, Headers cabeceras, String cuerpo) {
            this.metodo = metodo;
            this.ruta = ruta;
            this.cabeceras = cabeceras;
            this.cuerpo = cuerpo;
        }
    }

    final MockWebServer servidor = new MockWebServer();
    final List<Pet> peticiones = new ArrayList<>();
    final List<Double> esperas = new ArrayList<>();
    final Trevol trevol;
    private final AtomicInteger n = new AtomicInteger();
    private final AtomicInteger u = new AtomicInteger();

    /** Cada elemento de {@code respuestas} es una {@link Resp} o una {@code Function<Pet, Resp>}; la última se repite. */
    Banco(String llave, Function<Trevol.Builder, Trevol.Builder> opciones, Object... respuestas) {
        servidor.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                Pet p = new Pet(r.getMethod(), r.getPath(), r.getHeaders(), r.getBody().readString(StandardCharsets.UTF_8));
                int i;
                synchronized (peticiones) {
                    peticiones.add(p);
                    i = Math.min(n.getAndIncrement(), respuestas.length - 1);
                }
                Object x = respuestas[i];
                @SuppressWarnings("unchecked")
                Resp resp = x instanceof Resp ? (Resp) x : ((Function<Pet, Resp>) x).apply(p);
                if (resp.cortar) return new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);
                MockResponse mr = new MockResponse().setResponseCode(resp.estado).setHeader("Content-Type", resp.tipo).setBody(new Buffer().write(resp.cuerpo));
                resp.cabeceras.forEach(mr::addHeader);
                return mr;
            }
        });
        try {
            servidor.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Trevol.Builder b = Trevol.builder().apiKey(llave).baseUrl(servidor.url("/v1").toString())
                .internos(esperas::add, () -> 1.0, () -> String.format("00000000-0000-4000-8000-%012d", u.incrementAndGet()), java.time.Instant::now);
        this.trevol = opciones.apply(b).build();
    }

    Banco(Object... respuestas) { this(LLAVE_TEST, b -> b, respuestas); }

    @Override
    public void close() throws IOException { servidor.shutdown(); }

    static Path datos(String nombre) { return Path.of("pruebas", "datos", nombre); }

    static <T> T leer(String nombre, Class<T> clase) {
        try {
            return GSON.fromJson(Files.readString(datos(nombre), StandardCharsets.UTF_8), clase);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> leerMapa(String nombre) { return leer(nombre, HashMap.class); }
}
