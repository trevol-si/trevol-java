// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.EmailResendRequest;

public class EjemploSendInvoiceEmail {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        EmailResendRequest entrada = EmailResendRequest.fromJson("""
            {}
            """);
        var resultado = trevol.invoices().sendInvoiceEmail("inv_3f0c1a2b3f0c1a2b3f0c1a2b3f0c1a2b")
                .emailResendRequest(entrada)
                // .idempotencyKey("VENTA-2026-000123") // opcional: si falta, el SDK pone una y la repite en los reintentos
                .execute();
        System.out.println(resultado);
    }
}
