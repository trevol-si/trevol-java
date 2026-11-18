// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.PaperNumberingRangeInput;

public class EjemploCreatePaperNumberingRange {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        PaperNumberingRangeInput entrada = PaperNumberingRangeInput.fromJson("""
            {
              "resolution_number": "18764000004521",
              "prefix": "TP",
              "from": 1,
              "to": 5000,
              "valid_from": "2026-01-15",
              "valid_to": "2028-01-15"
            }
            """);
        var resultado = trevol.paperNumberingRanges().createPaperNumberingRange(entrada)
                // .idempotencyKey("VENTA-2026-000123") // opcional: si falta, el SDK pone una y la repite en los reintentos
                .execute();
        System.out.println(resultado);
    }
}
