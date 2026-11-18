// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.InvoiceBatchList;

public class EjemploListInvoiceBatches {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        for (var item : trevol.paginate(
                cursor -> trevol.invoiceBatches().listInvoiceBatches().limit(100).startingAfter(cursor).execute(),
                InvoiceBatchList::getData)) {
            System.out.println(item.getId());
        }
    }
}
