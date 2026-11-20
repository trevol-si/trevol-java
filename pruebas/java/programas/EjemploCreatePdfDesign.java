// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.PdfDesignInput;

public class EjemploCreatePdfDesign {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        PdfDesignInput entrada = PdfDesignInput.fromJson("""
            {
              "template": "modern",
              "name": "Moderno verde",
              "accent_color": "#118305",
              "font": "inter",
              "logo": "company",
              "set_as_default": true
            }
            """);
        var resultado = trevol.pdfDesigns().createPdfDesign(entrada)
                // .idempotencyKey("VENTA-2026-000123") // opcional: si falta, el SDK pone una y la repite en los reintentos
                .execute();
        System.out.println(resultado);
    }
}
