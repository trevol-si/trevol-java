/*
 * Prueba de humo REAL en habilitación (docs/api/43 §2.10.4). NO es parte de la suite: la corre una persona, con una llave de PRUEBAS.
 *
 *   mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 *   TREVOL_API_KEY=tvl_test_… java -cp "target/classes:$(cat target/cp.txt)" pruebas/humo/Humo.java
 *
 * Solo lecturas y un preview (el SDK de Java NO emite). Se NIEGA a correr si la llave no es tvl_test_. TREVOL_BASE_URL (opcional) apunta a otro
 * servidor, p. ej. un Prism local.
 */
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import si.trevol.NotFoundException;
import si.trevol.Trevol;
import si.trevol.Webhook;
import si.trevol.generado.model.InvoiceInput;
import si.trevol.generado.model.InvoiceList;

public class Humo {
    static int total = 0;
    static int fallas = 0;

    static void paso(String nombre, Callable<String> f) {
        total++;
        try {
            String detalle = f.call();
            System.out.println("ok   " + nombre + (detalle == null || detalle.isEmpty() ? "" : " — " + detalle));
        } catch (Exception e) { // es una prueba de humo: cuenta cualquier fallo
            fallas++;
            System.out.println("FALLA " + nombre + " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        String llave = System.getenv("TREVOL_API_KEY") == null ? "" : System.getenv("TREVOL_API_KEY");
        if (!llave.matches("tvl_test_[0-9a-f]{64}")) {
            System.err.println("humo: me niego a correr si TREVOL_API_KEY no es una llave de PRUEBAS (tvl_test_…). Nunca se prueba con tvl_live_.");
            System.exit(2);
        }
        Trevol.Builder b = Trevol.builder().apiKey(llave).environment(Trevol.Environment.TEST);
        if (System.getenv("TREVOL_BASE_URL") != null) b.baseUrl(System.getenv("TREVOL_BASE_URL"));
        Trevol trevol = b.build();

        paso("getHealth", () -> { trevol.general().getHealth().execute(); return ""; });
        paso("retrieveCompany", () -> {
            var c = trevol.general().retrieveCompany().execute();
            if (!"test".equals(c.getEnvironment())) throw new IllegalStateException("la empresa dice environment=" + c.getEnvironment());
            return c.getName() + " (" + c.getNit() + ")";
        });
        paso("previewInvoice", () -> {
            var p = trevol.invoices().previewInvoice(InvoiceInput.fromJson("""
                {"external_id": "HUMO-PREVIEW", "customer": {"final_consumer": true},
                 "items": [{"description": "Prueba de humo del SDK de Trevol", "quantity": "1", "unit_price": "1000", "taxes": [{"code": "01", "rate": "19"}]}],
                 "payment": {"form": "cash", "methods": [{"code": "10"}]}}
                """)).execute();
            return "total " + (p.getTotals() == null ? "?" : p.getTotals().get("total"));
        });
        paso("listInvoices + paginate (3)", () -> {
            trevol.invoices().listInvoices().limit(3).execute();
            int n = 0;
            for (var f : trevol.paginate(c -> trevol.invoices().listInvoices().limit(3).startingAfter(c).execute(), InvoiceList::getData)) {
                if (++n == 3) break;
            }
            return n + " facturas";
        });
        paso("retrieveInvoice inexistente → NotFoundException con request_id", () -> {
            try {
                trevol.invoices().retrieveInvoice("inv_" + "0".repeat(32)).execute();
            } catch (NotFoundException e) {
                if (e.getRequestId() == null || !e.getRequestId().startsWith("req_")) throw new IllegalStateException("sin request_id");
                return e.getRequestId();
            }
            throw new IllegalStateException("no lanzó NotFoundException");
        });
        paso("Webhook.verify (firmado ahora, con un secreto local)", () -> {
            byte[] clave = "trevol-humo-secreto-de-32-bytes!".getBytes(StandardCharsets.UTF_8);
            String id = "evt_" + "1".repeat(32);
            String cuerpo = "{\"id\":\"" + id + "\",\"object\":\"event\",\"type\":\"webhook.test\",\"timestamp\":\"" + Instant.now() + "\",\"environment\":\"test\",\"api_version\":\"2026-10-22\",\"data\":{}}";
            long ts = Instant.now().getEpochSecond();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            String firma = "v1," + Base64.getEncoder().encodeToString(mac.doFinal((id + "." + ts + "." + cuerpo).getBytes(StandardCharsets.UTF_8)));
            return new Webhook("whsec_" + Base64.getEncoder().encodeToString(clave)).verify(cuerpo, Map.of("webhook-id", id, "webhook-timestamp", Long.toString(ts), "webhook-signature", firma)).getType();
        });

        System.out.println("\n" + (total - fallas) + "/" + total + " pasos bien");
        System.exit(fallas > 0 ? 1 : 0);
    }
}
