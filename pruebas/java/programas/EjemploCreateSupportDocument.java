// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.SupportDocumentInput;

public class EjemploCreateSupportDocument {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        SupportDocumentInput entrada = SupportDocumentInput.fromJson("""
            {
              "external_id": "OC-991",
              "seller": {
                "resident": true,
                "person_type": "natural",
                "document_type": "31",
                "document_number": "1020304050",
                "name": "PEREZ GOMEZ ANA MARIA",
                "address": "Vereda El Silencio, finca La Esperanza",
                "municipality_code": "25053",
                "postal_code": "252007",
                "tax_responsibilities": [
                  "R-99-PN"
                ]
              },
              "items": [
                {
                  "description": "Leche cruda",
                  "unit": "LTR",
                  "quantity": "120",
                  "unit_price": "1800"
                }
              ],
              "payment": {
                "form": "cash",
                "methods": [
                  {
                    "code": "10"
                  }
                ]
              },
              "notes": [
                "Compra de la semana 42 de octubre"
              ]
            }
            """);
        var resultado = trevol.supportDocuments().createSupportDocument(entrada)
                // .idempotencyKey("VENTA-2026-000123") // opcional: si falta, el SDK pone una y la repite en los reintentos
                .execute();
        System.out.println(resultado);
    }
}
