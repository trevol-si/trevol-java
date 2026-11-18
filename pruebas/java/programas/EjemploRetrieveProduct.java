// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;

public class EjemploRetrieveProduct {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        var resultado = trevol.products().retrieveProduct("prod_3f0c1a2b3f0c1a2b3f0c1a2b3f0c1a2b").execute();
        System.out.println(resultado);
    }
}
