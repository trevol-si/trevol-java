/*
 * Prueba de humo REAL en habilitación (docs/api/43 §2.10.4). NO es parte de la suite: la corre una persona, con una llave de PRUEBAS.
 *
 *   mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 *   TREVOL_API_KEY=tvl_test_… java -cp "target/classes:$(cat target/cp.txt)" pruebas/humo/Humo.java
 *
 * Lecturas, un preview y un error tipado (validation_error); con TREVOL_HUMO_EMITIR=1 además EMITE una factura en habilitación, espera a que
 * quede final y descarga el PDF. Se NIEGA a correr si la llave no es tvl_test_. TREVOL_BASE_URL (opcional) apunta a otro
 * servidor, p. ej. un Prism local.
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import si.trevol.NotFoundException;
import si.trevol.Trevol;
import si.trevol.ValidationException;
import si.trevol.Webhook;
import si.trevol.generado.model.InvoiceInput;
import si.trevol.generado.model.InvoiceList;

public class Humo {
    static String factura(String externo, boolean conLineas) {
        return "{\"external_id\": \"" + externo + "\", \"customer\": {\"final_consumer\": true}, \"items\": "
            + (conLineas ? "[{\"description\": \"Prueba de humo del SDK de Trevol\", \"quantity\": \"1\", \"unit_price\": \"1000\", \"taxes\": [{\"code\": \"01\", \"rate\": \"19\"}]}]" : "[]")
            + ", \"payment\": {\"form\": \"cash\", \"methods\": [{\"code\": \"10\"}]}}";
    }

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
            var p = trevol.invoices().previewInvoice(InvoiceInput.fromJson(factura("HUMO-PREVIEW", true))).execute();
            return "total " + (p.getTotals() == null ? "?" : p.getTotals().get("total"));
        });
        paso("previewInvoice sin líneas → ValidationException (code validation_error)", () -> {
            try {
                trevol.invoices().previewInvoice(InvoiceInput.fromJson(factura("HUMO-INVALIDA", false))).execute();
            } catch (ValidationException e) {
                if (!"validation_error".equals(e.getCode())) throw new IllegalStateException("code = " + e.getCode());
                return e.getStatus() + " " + e.getCode() + " " + (e.getErrors().isEmpty() ? "" : e.getErrors().get(0).getPointer());
            }
            throw new IllegalStateException("no lanzó ValidationException");
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

        if ("1".equals(System.getenv("TREVOL_HUMO_EMITIR"))) {
            String[] id = {null};
            paso("createInvoice (EMITE en habilitación)", () -> {
                var d = trevol.invoices().createInvoice(InvoiceInput.fromJson(factura("HUMO-JAVA-" + Instant.now().getEpochSecond(), true))).execute();
                id[0] = d.getId();
                return d.getId() + " " + d.getStatus();
            });
            if (id[0] != null) {
                paso("espera un estado final (hasta 90 s)", () -> {
                    long limite = System.currentTimeMillis() + 90_000;
                    for (;;) {
                        String estado = trevol.invoices().retrieveInvoice(id[0]).execute().getStatus();
                        if (!java.util.List.of("pending", "sending", "processing").contains(estado)) {
                            if (!"accepted".equals(estado)) throw new IllegalStateException("status = " + estado);
                            return estado;
                        }
                        if (System.currentTimeMillis() > limite) throw new IllegalStateException("status = " + estado);
                        Thread.sleep(3000);
                    }
                });
                paso("downloadInvoicePdf", () -> {
                    byte[] bytes = Files.readAllBytes(trevol.invoices().downloadInvoicePdf(id[0]).execute().toPath());
                    if (!new String(bytes, 0, Math.min(4, bytes.length), StandardCharsets.ISO_8859_1).equals("%PDF")) throw new IllegalStateException("no es un PDF");
                    return "PDF " + bytes.length + " bytes";
                });
            }
        } else {
            System.out.println("(no se emite nada: define TREVOL_HUMO_EMITIR=1 para emitir una factura en habilitación)");
        }

        System.out.println("\n" + (total - fallas) + "/" + total + " pasos bien");
        System.exit(fallas > 0 ? 1 : 0);
    }
}
