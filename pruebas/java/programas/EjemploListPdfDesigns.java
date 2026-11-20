// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.PdfDesignList;

public class EjemploListPdfDesigns {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        for (var item : trevol.paginate(
                cursor -> trevol.pdfDesigns().listPdfDesigns().limit(100).startingAfter(cursor).execute(),
                PdfDesignList::getData)) {
            System.out.println(item.getId());
        }
    }
}
