// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.WebhookEndpointInput;

public class EjemploCreateWebhookEndpoint {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        WebhookEndpointInput entrada = WebhookEndpointInput.fromJson("""
            {
              "url": "https://tu-servidor.co/webhooks/trevol",
              "description": "Producción: facturación",
              "events": [
                "invoice.accepted",
                "invoice.rejected",
                "credit_note.accepted"
              ]
            }
            """);
        var resultado = trevol.webhookEndpoints().createWebhookEndpoint(entrada).execute();
        System.out.println(resultado);
    }
}
