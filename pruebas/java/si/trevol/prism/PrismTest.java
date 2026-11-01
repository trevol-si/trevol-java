package si.trevol.prism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.Gson;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import si.trevol.ConflictException;
import si.trevol.PreconditionException;
import si.trevol.Trevol;
import si.trevol.TrevolException;
import si.trevol.TrevolJson;
import si.trevol.generado.model.Document;
import si.trevol.generado.model.EventList;
import si.trevol.generado.model.InvoiceInput;
import si.trevol.generado.model.InvoiceList;
import si.trevol.generado.model.NoteInput;
import si.trevol.generado.model.RadianEventInput;
import si.trevol.generado.model.WebhookEndpointInput;

/**
 * Contra Prism (servidor simulado desde docs/api/openapi.json, docs/api/43 §2.10.2):
 *
 * <pre>sdks/herramientas/prism.sh -- bash -c 'cd sdks/java &amp;&amp; mvn test -Dtest=PrismTest'</pre>
 *
 * Prism valida cada petición contra la especificación PÚBLICA: si el SDK serializa mal un cuerpo, olvida una cabecera o la Idempotency-Key,
 * responde 4xx y la prueba falla (el motivo viene en la cabecera {@code sl-violations}, que el error conserva).
 */
@EnabledIfEnvironmentVariable(named = "TREVOL_PRISM_URL", matches = ".+")
class PrismTest {
    static final String URL = System.getenv("TREVOL_PRISM_URL");
    static final String LLAVE = System.getenv("TREVOL_API_KEY") != null ? System.getenv("TREVOL_API_KEY") : "tvl_test_" + "a1".repeat(32);
    Trevol t;

    @BeforeEach
    void cliente() {
        t = Trevol.builder().apiKey(LLAVE).baseUrl(URL).maxRetries(0).build();
    }

    @SuppressWarnings("unchecked")
    static String ejemplo(String op, String nombre) throws Exception {
        List<Map<String, Object>> ops = new Gson().fromJson(Files.readString(Path.of("pruebas", "datos", "operaciones.json"), StandardCharsets.UTF_8), List.class);
        for (Map<String, Object> o : ops) {
            if (!op.equals(o.get("operationId"))) continue;
            for (Map<String, Object> e : (List<Map<String, Object>>) o.get("ejemplos")) {
                if (nombre == null || nombre.equals(e.get("nombre"))) return new Gson().toJson(e.get("valor"));
            }
        }
        throw new IllegalStateException("no hay ejemplo " + nombre + " de " + op);
    }

    interface Llamada<T> { T hacer() throws Exception; }

    <T> T conViolaciones(Llamada<T> f) throws Exception {
        try {
            return f.hacer();
        } catch (TrevolException e) {
            List<String> v = e.getHeaders().entrySet().stream().filter(x -> x.getKey().equalsIgnoreCase("sl-violations")).map(x -> String.join(",", x.getValue())).toList();
            if (!v.isEmpty()) fail("Prism rechazó la petición (" + e.getStatus() + "): " + v);
            throw e;
        }
    }

    /** Un cliente cuyas peticiones llevan {@code Prefer} (Prism devuelve ese error o ejemplo). */
    Trevol conPrefer(String prefer, int reintentos) {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(c -> c.proceed(c.request().newBuilder().header("Prefer", prefer).build()))
                .connectTimeout(Duration.ofSeconds(10)).readTimeout(Duration.ofSeconds(30)).build();
        return Trevol.builder().apiKey(LLAVE).baseUrl(URL).maxRetries(reintentos).httpClient(http).build();
    }

    @Test
    void creaUnaFactura201ConElCuerpoDeEjemplo() throws Exception {
        Document doc = conViolaciones(() -> t.invoices().createInvoice(InvoiceInput.fromJson(ejemplo("createInvoice", null))).execute());
        assertEquals("invoice", doc.getObject());
    }

    @Test
    void creaUnaNotaCreditoYCalculaUnPreview() throws Exception {
        var nc = conViolaciones(() -> t.creditNotes().createCreditNote(NoteInput.fromJson(ejemplo("createCreditNote", null))).execute());
        var p = conViolaciones(() -> t.invoices().previewInvoice(InvoiceInput.fromJson(ejemplo("previewInvoice", null))).execute());
        assertNotNull(nc);
        assertEquals("preview", p.getObject());
        assertNotNull(p.getTotals());
    }

    @Test
    void corrigeUnaFacturaRechazadaConPut() throws Exception {
        assertNotNull(conViolaciones(() -> t.invoices().correctInvoice("inv_" + "0".repeat(32), InvoiceInput.fromJson(ejemplo("correctInvoice", null))).execute()));
    }

    @Test
    void listaFacturasYEventosEIteraConTopeDe3() throws Exception {
        List<String> vistos = new ArrayList<>();
        for (Document f : t.paginate(c -> t.invoices().listInvoices().limit(2).startingAfter(c).execute(), InvoiceList::getData)) {
            vistos.add(f.getId());
            if (vistos.size() == 3) break; // Prism repite siempre la misma página con has_more=true (C8): el tope es de la prueba
        }
        assertTrue(!vistos.isEmpty());
        int n = 0;
        for (var e : t.paginate(c -> t.events().listEvents().startingAfter(c).execute(), EventList::getData)) {
            assertNotNull(e.getId());
            if (++n == 3) break;
        }
        assertTrue(n > 0);
    }

    @Test
    void descargaPdfXmlZipYRespuestaDianLosBytesLleganYElTipoEsElCorrecto() throws Exception {
        String id = "inv_" + "0".repeat(32);
        for (File f : List.of(t.invoices().downloadInvoicePdf(id).execute(), t.invoices().downloadInvoiceXml(id).execute(),
                t.invoices().downloadInvoiceZip(id).execute(), t.invoices().downloadInvoiceDianResponse(id).execute())) {
            assertTrue(f.length() > 0, f.getName());
            f.delete();
        }
        var r = t.invoices().downloadInvoicePdf(id).executeWithHttpInfo();
        assertEquals(200, r.getStatusCode());
        assertTrue(String.join(",", r.getHeaders().entrySet().stream().filter(x -> x.getKey().equalsIgnoreCase("content-type")).findFirst().get().getValue()).contains("application/pdf"));
    }

    @Test
    void preferCode422SobreUnEventoRadianEsPreconditionExceptionTacitAcceptanceNotDue() throws Exception {
        Trevol c = conPrefer("code=422, example=tacita", 0);
        PreconditionException e = assertThrows(PreconditionException.class, () -> c.radianEvents().createRadianEvent(RadianEventInput.fromJson(ejemplo("createRadianEvent", "tacita"))).execute());
        assertEquals("tacit_acceptance_not_due", e.getCode());
        assertEquals(422, e.getStatus());
    }

    @Test
    void preferCode409SobreUnEventoRadianEsConflictException() throws Exception {
        Trevol c = conPrefer("code=409, example=duplicado", 0);
        ConflictException e = assertThrows(ConflictException.class, () -> c.radianEvents().createRadianEvent(RadianEventInput.fromJson(ejemplo("createRadianEvent", "acuse"))).execute());
        assertEquals(409, e.getStatus());
        assertTrue(((String) e.getProblem().get("existing_id")).startsWith("rev_"));
    }

    @Test
    void preferCode429EsUnaExcepcionConStatus429RetryableYLaClave() throws Exception {
        Trevol c = conPrefer("code=429", 0);
        TrevolException e = assertThrows(TrevolException.class, () -> c.invoices().createInvoice(InvoiceInput.fromJson(ejemplo("createInvoice", null))).execute());
        assertEquals(429, e.getStatus());
        assertEquals(Boolean.TRUE, e.getRetryable());
        assertNotNull(e.getIdempotencyKey());
    }

    @Test
    void creaUnEndpointDeWebhooksYMandaLaPrueba202() throws Exception {
        var ep = conViolaciones(() -> t.webhookEndpoints().createWebhookEndpoint(WebhookEndpointInput.fromJson(ejemplo("createWebhookEndpoint", null))).execute());
        var entrega = conViolaciones(() -> t.webhookEndpoints().testWebhookEndpoint("we_" + "0".repeat(32)).execute());
        assertEquals("webhook_endpoint", ep.getObject());
        assertNotNull(entrega);
    }

    @Test
    void companyYAcquirerDevuelvenModelosNoVoid() throws Exception {
        var c = conViolaciones(() -> t.general().retrieveCompany().execute());
        var a = conViolaciones(() -> t.general().retrieveDianAcquirer("31", "900373591").execute());
        assertEquals("company", c.getObject());
        assertEquals("acquirer", a.getObject());
    }

    @Test
    void reintentaContraPrismConLaMismaClaveYAgotaConElErrorDel503() throws Exception {
        Trevol c = conPrefer("code=503", 1);
        TrevolException e = assertThrows(TrevolException.class, () -> c.invoices().createInvoice(InvoiceInput.fromJson(ejemplo("createInvoice", null))).idempotencyKey("PRUEBA-PRISM-1").execute());
        assertEquals(503, e.getStatus());
        assertEquals("PRUEBA-PRISM-1", e.getIdempotencyKey());
    }
}
