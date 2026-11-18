// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.CatalogProductInput;

public class EjemploCreateProduct {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        CatalogProductInput entrada = CatalogProductInput.fromJson("""
            {
              "external_id": "ERP-SKU-CAF500",
              "code": "CAF-500",
              "name": "Café especial tostado 500 g",
              "unit": "94",
              "unit_price": "32000",
              "taxes": [
                {
                  "code": "01",
                  "rate": "5"
                }
              ]
            }
            """);
        var resultado = trevol.products().createProduct(entrada)
                // .idempotencyKey("VENTA-2026-000123") // opcional: si falta, el SDK pone una y la repite en los reintentos
                .execute();
        System.out.println(resultado);
    }
}
