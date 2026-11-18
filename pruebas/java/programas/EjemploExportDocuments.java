// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import java.io.File;
import si.trevol.Trevol;

public class EjemploExportDocuments {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        File archivo = trevol.reports().exportDocuments("csv", "2026-09-01", "2026-10-31").execute();
        System.out.println(archivo.getPath()); // un File temporal con el CSV
    }
}
