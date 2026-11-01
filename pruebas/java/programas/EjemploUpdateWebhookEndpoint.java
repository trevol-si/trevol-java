// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.WebhookEndpointUpdate;

public class EjemploUpdateWebhookEndpoint {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        WebhookEndpointUpdate entrada = WebhookEndpointUpdate.fromJson("""
            {
              "description": "Pausado mientras migramos el servidor",
              "status": "paused"
            }
            """);
        var resultado = trevol.webhookEndpoints().updateWebhookEndpoint("we_3f0c1a2b3f0c1a2b3f0c1a2b3f0c1a2b", entrada).execute();
        System.out.println(resultado);
    }
}
