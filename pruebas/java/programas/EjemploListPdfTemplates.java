// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;

public class EjemploListPdfTemplates {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        var lista = trevol.pdfTemplates().listPdfTemplates().execute();
        for (var item : lista.getData()) {
            System.out.println(item.getKey());
        }
    }
}
