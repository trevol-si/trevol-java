package si.trevol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import si.trevol.generado.model.Document;
import si.trevol.generado.model.InvoiceList;

class PaginacionTest {
    static Document doc(String n) { return new Document().id("inv_" + n); }

    static InvoiceList pagina(boolean hay, String cursor, String... ids) {
        InvoiceList l = new InvoiceList().hasMore(hay).nextCursor(cursor);
        for (String id : ids) l.addDataItem(doc(id));
        return l;
    }

    @Test
    void recorreTresPaginasFalsasConNextCursorYSeDetiene() {
        List<String> pedidos = new ArrayList<>();
        List<InvoiceList> paginas = List.of(pagina(true, "inv_2", "1", "2"), pagina(true, "inv_4", "3", "4"), pagina(false, null, "5"));
        List<String> ids = new ArrayList<>();
        for (Document f : Paginacion.paginar((String c) -> { pedidos.add(c); return paginas.get(pedidos.size() - 1); }, InvoiceList::getData)) ids.add(f.getId());
        assertEquals(List.of("inv_1", "inv_2", "inv_3", "inv_4", "inv_5"), ids);
        assertEquals(Arrays.asList(null, "inv_2", "inv_4"), pedidos);
    }

    @Test
    void siHasMoreVieneSinNextCursorUsaElIdDelUltimo() {
        List<String> pedidos = new ArrayList<>();
        List<InvoiceList> paginas = List.of(pagina(true, null, "1", "2"), pagina(false, null, "3"));
        int n = 0;
        for (Document ignorado : Paginacion.paginar((String c) -> { pedidos.add(c); return paginas.get(pedidos.size() - 1); }, InvoiceList::getData)) n++;
        assertEquals(3, n);
        assertEquals(Arrays.asList(null, "inv_2"), pedidos);
    }

    @Test
    void esPerezosa() {
        List<String> llamadas = new ArrayList<>();
        Iterator<Document> it = Paginacion.paginar((String c) -> { llamadas.add(c); return pagina(true, "inv_2", "1", "2"); }, InvoiceList::getData).iterator();
        it.next();
        assertEquals(1, llamadas.size());
    }

    @Test
    void unCursorQueNoAvanzaOUnaPaginaVaciaNoDejanElBucleGirando() {
        int vistos = 0;
        for (Document ignorado : Paginacion.paginar((String c) -> pagina(true, "inv_1", "1"), InvoiceList::getData)) vistos++;
        assertTrue(vistos <= 3);
        assertEquals(0, Paginacion.paginar((String c) -> pagina(true, "x"), InvoiceList::getData).iterator().hasNext() ? 1 : 0);
    }

    @Test
    void clientPaginateConUnMetodoGeneradoYElServidorFalso() throws Exception {
        try (Banco b = new Banco(
                Banco.json(200, "{\"object\":\"list\",\"data\":[{\"id\":\"inv_1\"},{\"id\":\"inv_2\"}],\"has_more\":true,\"next_cursor\":\"inv_2\"}"),
                Banco.json(200, "{\"object\":\"list\",\"data\":[{\"id\":\"inv_3\"}],\"has_more\":false,\"next_cursor\":null}"))) {
            Trevol t = b.trevol;
            List<String> ids = new ArrayList<>();
            for (Document f : t.paginate(cursor -> t.invoices().listInvoices().limit(2).startingAfter(cursor).execute(), InvoiceList::getData)) ids.add(f.getId());
            assertEquals(List.of("inv_1", "inv_2", "inv_3"), ids);
            assertEquals("/v1/invoices?limit=2", b.peticiones.get(0).ruta);
            assertEquals("/v1/invoices?limit=2&starting_after=inv_2", b.peticiones.get(1).ruta);
        }
    }
}
