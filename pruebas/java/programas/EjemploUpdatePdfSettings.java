// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.PdfSettingsUpdate;

public class EjemploUpdatePdfSettings {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        PdfSettingsUpdate entrada = PdfSettingsUpdate.fromJson("""
            {
              "letter_design_id": "pdd_7d3e9a107d3e9a107d3e9a107d3e9a10",
              "receipt_design_id": "pdd_41b2c6f041b2c6f041b2c6f041b2c6f0"
            }
            """);
        var resultado = trevol.companySettings().updatePdfSettings(entrada).execute();
        System.out.println(resultado);
    }
}
