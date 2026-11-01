// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.RadianEventInput;

public class EjemploCreateRadianEvent {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        RadianEventInput entrada = RadianEventInput.fromJson("""
            {
              "external_id": "ERP-ACUSE-DA1234",
              "event_code": "030",
              "invoice": {
                "cufe": "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd",
                "number": "DA1234",
                "type": "01",
                "issuer": {
                  "nit": "900373427",
                  "check_digit": 3,
                  "document_type": "31",
                  "name": "DISTRIBUIDORA LOS ANDES SAS"
                }
              }
            }
            """);
        var resultado = trevol.radianEvents().createRadianEvent(entrada)
                // .idempotencyKey("VENTA-2026-000123") // opcional: si falta, el SDK pone una y la repite en los reintentos
                .execute();
        System.out.println(resultado);
    }
}
