// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.DebitNoteList;

public class EjemploListDebitNotes {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        for (var item : trevol.paginate(
                cursor -> trevol.debitNotes().listDebitNotes().limit(100).startingAfter(cursor).execute(),
                DebitNoteList::getData)) {
            System.out.println(item.getId());
        }
    }
}
