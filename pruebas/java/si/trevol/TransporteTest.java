package si.trevol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TransporteTest {
    @SuppressWarnings("unchecked")
    static Stream<Map<String, Object>> casos() {
        return ((List<Map<String, Object>>) Banco.leerMapa("transporte.json").get("casos")).stream();
    }

    @Test
    void hayCasos() {
        assertTrue(casos().count() >= 50);
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void decidirConPruebasDatosTransporteJson(Map<String, Object> caso) {
        Map<String, Object> r = (Map<String, Object>) caso.get("resultado");
        Transporte.Resultado resultado = "red".equals(r.get("tipo")) ? Transporte.Resultado.red()
                : Transporte.Resultado.respuesta(((Number) r.get("estado")).intValue(), (String) r.get("code"), (Boolean) r.get("retryable"),
                        r.get("retryAfter") == null ? null : ((Number) r.get("retryAfter")).doubleValue());
        Transporte.Decision d = Transporte.decidir(((Number) caso.get("intento")).intValue(), (String) caso.get("reintento"),
                ((Number) caso.get("maxReintentos")).intValue(), ((Number) caso.get("maxRetryAfter")).doubleValue(), ((Number) caso.get("azar")).doubleValue(), resultado);
        Map<String, Object> e = (Map<String, Object>) caso.get("esperado");
        assertEquals(e.get("reintentar"), d.reintentar, (String) caso.get("nombre"));
        if (e.containsKey("espera")) assertEquals(((Number) e.get("espera")).doubleValue(), d.espera, 1e-9);
        if (e.containsKey("motivo")) assertEquals(e.get("motivo"), d.motivo);
        if (!(Boolean) e.get("reintentar")) assertNull(d.espera);
    }

    @Test
    void laEsperaPropiaCreceConTopeDe8YUnFactorEntre075Y1() {
        double[] esperados = {0.5, 1, 2, 4, 8, 8, 8};
        int[] intentos = {1, 2, 3, 4, 5, 6, 9};
        for (int i = 0; i < intentos.length; i++) assertEquals(esperados[i], Transporte.esperaPropia(intentos[i], 1.0), 1e-9);
        assertEquals(0.375, Transporte.esperaPropia(1, 0.0), 1e-9);
        assertEquals(3.0, Transporte.esperaPropia(4, 0.0), 1e-9);
    }
}
