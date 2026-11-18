// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.PaperNumberingRangeUpdate;

public class EjemploUpdatePaperNumberingRange {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        PaperNumberingRangeUpdate entrada = PaperNumberingRangeUpdate.fromJson("""
            {
              "valid_to": "2028-06-30"
            }
            """);
        var resultado = trevol.paperNumberingRanges().updatePaperNumberingRange("pnr_3f0c1a2b3f0c1a2b3f0c1a2b3f0c1a2b", entrada).execute();
        System.out.println(resultado);
    }
}
