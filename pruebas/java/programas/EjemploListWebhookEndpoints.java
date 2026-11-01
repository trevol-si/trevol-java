// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;

public class EjemploListWebhookEndpoints {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        var lista = trevol.webhookEndpoints().listWebhookEndpoints().execute();
        for (var item : lista.getData()) {
            System.out.println(item.getId());
        }
    }
}
