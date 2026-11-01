// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.SupportDocumentAdjustmentInput;

public class EjemploCreateSupportDocumentAdjustment {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        SupportDocumentAdjustmentInput entrada = SupportDocumentAdjustmentInput.fromJson("""
            {
              "external_id": "OC-991-ANULA",
              "concept": "2",
              "description": "Anulación: la compra de leche se registró dos veces en el ERP",
              "support_document": {
                "id": "sd_3f0c1a2b3f0c1a2b3f0c1a2b3f0c1a2b"
              },
              "items": [
                {
                  "description": "Leche cruda",
                  "unit": "LTR",
                  "quantity": "120",
                  "unit_price": "1800"
                }
              ]
            }
            """);
        var resultado = trevol.supportDocumentAdjustments().createSupportDocumentAdjustment(entrada)
                // .idempotencyKey("VENTA-2026-000123") // opcional: si falta, el SDK pone una y la repite en los reintentos
                .execute();
        System.out.println(resultado);
    }
}
