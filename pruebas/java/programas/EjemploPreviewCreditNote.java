// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.NoteInput;

public class EjemploPreviewCreditNote {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        NoteInput entrada = NoteInput.fromJson("""
            {
              "external_id": "DEV-2026-000045",
              "concept": "1",
              "description": "Devolución de una unidad por empaque dañado",
              "invoice": {
                "id": "inv_3f0c1a2b3f0c1a2b3f0c1a2b3f0c1a2b"
              },
              "items": [
                {
                  "description": "Café especial 500 g",
                  "code": "CAF-500",
                  "unit": "94",
                  "quantity": "1",
                  "unit_price": "32000",
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
        var resultado = trevol.creditNotes().previewCreditNote(entrada).execute();
        System.out.println(resultado);
    }
}
