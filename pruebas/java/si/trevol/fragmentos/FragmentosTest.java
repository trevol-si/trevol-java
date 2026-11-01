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
        assertEquals(109, ops.size());
        try (Stream<Path> s = Files.list(programas())) {
            List<String> archivos = s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".java")).sorted().collect(Collectors.toList());
            assertEquals(ops.stream().map(o -> "Ejemplo" + Character.toUpperCase(o.charAt(0)) + o.substring(1) + ".java").sorted().collect(Collectors.toList()), archivos);
        }
    }

    @ParameterizedTest
    @MethodSource("ops")
    @EnabledIfEnvironmentVariable(named = "TREVOL_PRISM_URL", matches = ".+")
    void elFragmentoCorreContraPrism(String op) throws Exception {
        Method main = Class.forName(clase(op)).getMethod("main", String[].class);
        main.invoke(null, (Object) new String[0]);
    }

    @Test
    void cadaProgramaCompiladoTieneMain() throws Exception {
        for (String op : operaciones()) assertTrue(Class.forName(clase(op)).getMethod("main", String[].class) != null, op);
    }
}
