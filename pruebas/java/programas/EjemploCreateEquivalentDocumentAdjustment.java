// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.EquivalentDocumentAdjustmentInput;

public class EjemploCreateEquivalentDocumentAdjustment {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        EquivalentDocumentAdjustmentInput entrada = EquivalentDocumentAdjustmentInput.fromJson("""
            {
              "external_id": "TIQ-1-DEV",
              "note_type": "credit",
              "concept": "1",
              "equivalent_document": {
                "id": "pos_3f0c1a2b3f0c1a2b3f0c1a2b3f0c1a2b"
              },
              "items": [
                {
                  "description": "Devolución gaseosa",
                  "unit": "94",
                  "quantity": "1",
                  "unit_price": "3000",
                  "taxes": [
                    {
                      "code": "01",
                      "rate": "19"
                    }
                  ]
                }
              ]
            }
            """);
        var resultado = trevol.equivalentDocumentAdjustments().createEquivalentDocumentAdjustment(entrada)
                // .idempotencyKey("VENTA-2026-000123") // opcional: si falta, el SDK pone una y la repite en los reintentos
                .execute();
        System.out.println(resultado);
    }
}
