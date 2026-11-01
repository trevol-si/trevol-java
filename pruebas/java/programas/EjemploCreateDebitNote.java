// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.NoteInput;

public class EjemploCreateDebitNote {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        NoteInput entrada = NoteInput.fromJson("""
            {
              "external_id": "INT-2026-000012",
              "concept": "1",
              "description": "Intereses de mora de la factura de septiembre",
              "invoice": {
                "id": "inv_3f0c1a2b3f0c1a2b3f0c1a2b3f0c1a2b"
              },
              "items": [
                {
                  "description": "Intereses de mora",
                  "unit": "94",
                  "quantity": "1",
                  "unit_price": "45000"
                }
              ]
            }
            """);
        var resultado = trevol.debitNotes().createDebitNote(entrada)
                // .idempotencyKey("VENTA-2026-000123") // opcional: si falta, el SDK pone una y la repite en los reintentos
                .execute();
        System.out.println(resultado);
    }
}
