package si.trevol.fragmentos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Los fragmentos de la documentación, como programas (docs/api/43 §2.9): se COMPILAN (Maven los compila con las pruebas: un fragmento que no
 * compila rompe el build) y se CORREN contra Prism. Un fragmento que Prism rechaza porque el cuerpo no cumple la especificación rompe esta suite.
 *
 * <pre>sdks/herramientas/prism.sh -- bash -c 'cd sdks/java &amp;&amp; mvn test -Dtest=FragmentosTest'</pre>
 */
class FragmentosTest {
    static Path programas() { return Path.of("pruebas", "java", "programas"); }

    @SuppressWarnings("unchecked")
    static List<String> operaciones() throws IOException {
        Map<String, Object> d = new Gson().fromJson(Files.readString(Path.of("pruebas", "datos", "fragmentos.json"), StandardCharsets.UTF_8), Map.class);
        List<String> ops = new ArrayList<>(((Map<String, Object>) d.get("fragmentos")).keySet());
        ops.sort(null);
        return ops;
    }

    static Stream<String> ops() throws IOException { return operaciones().stream(); }

    static String clase(String op) { return "programas.Ejemplo" + Character.toUpperCase(op.charAt(0)) + op.substring(1); }

    @Test
    void laEstructura() throws IOException {
        List<String> ops = operaciones();
        assertEquals(180, ops.size()); // 0.4.0: + 19; 0.6.0: + 2 (el PDF de la DIAN)
        try (Stream<Path> s = Files.list(programas())) {
            List<String> archivos = s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".java")).sorted().collect(Collectors.toList());
            assertEquals(ops.stream().map(o -> "Ejemplo" + Character.toUpperCase(o.charAt(0)) + o.substring(1) + ".java").sorted().collect(Collectors.toList()), archivos);
        }
    }

    /**
     * Las operaciones de /v1/account piden una llave de administración: el SDK se niega a llamarlas con una tvl_ (ConfigurationException
     * antes de la red), así que su programa corre en un proceso aparte con TREVOL_API_KEY = una tva_ de prueba. Prism no distingue una de
     * otra (las dos son Bearer).
     */
    static final String LLAVE_ADMINISTRACION = "tva_test_" + "c4".repeat(32);

    @SuppressWarnings("unchecked")
    static Set<String> operacionesDeCuenta() throws IOException {
        List<Map<String, Object>> ops = new Gson().fromJson(Files.readString(Path.of("pruebas", "datos", "operaciones.json"), StandardCharsets.UTF_8), List.class);
        return ops.stream().filter(o -> "administracion".equals(o.get("llave"))).map(o -> (String) o.get("operationId")).collect(Collectors.toSet());
    }

    @Test
    void lasOperacionesDeCuentaSonDiez() throws IOException {
        Set<String> cuenta = operacionesDeCuenta();
        assertEquals(10, cuenta.size());
        assertTrue(operaciones().containsAll(cuenta), cuenta.toString());
    }

    @ParameterizedTest
    @MethodSource("ops")
    @EnabledIfEnvironmentVariable(named = "TREVOL_PRISM_URL", matches = ".+")
    void elFragmentoCorreContraPrism(String op) throws Exception {
        if (operacionesDeCuenta().contains(op)) {
            Path java = Path.of(System.getProperty("java.home"), "bin", "java");
            ProcessBuilder pb = new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"), clase(op)).redirectErrorStream(true);
            pb.environment().put("TREVOL_API_KEY", LLAVE_ADMINISTRACION);
            Process p = pb.start();
            String salida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(p.waitFor(60, TimeUnit.SECONDS), op + ": no terminó");
            assertEquals(0, p.exitValue(), op + ":\n" + salida);
            return;
        }
        Method main = Class.forName(clase(op)).getMethod("main", String[].class);
        main.invoke(null, (Object) new String[0]);
    }

    @Test
    void cadaProgramaCompiladoTieneMain() throws Exception {
        for (String op : operaciones()) assertTrue(Class.forName(clase(op)).getMethod("main", String[].class) != null, op);
    }
}
