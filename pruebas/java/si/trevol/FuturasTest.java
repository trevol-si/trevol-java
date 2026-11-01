package si.trevol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import si.trevol.generado.model.Document;
import si.trevol.generado.model.Event;
import si.trevol.generado.model.InvoiceList;

class FuturasTest {
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> casos() { return (List<Map<String, Object>>) Banco.leerMapa("respuestas-futuras.json").get("casos"); }

    static Stream<Map<String, Object>> respuestas() { return casos().stream().filter(c -> c.containsKey("operacion")); }

    static Stream<Map<String, Object>> eventos() { return casos().stream().filter(c -> c.containsKey("evento")); }

    @SuppressWarnings("unchecked")
    @ParameterizedTest(name = "{0}")
    @MethodSource("respuestas")
    void respuestasDelFuturoNoRompen(Map<String, Object> caso) throws Exception {
        int estado = ((Number) caso.get("estado")).intValue();
        String tipo = estado >= 400 ? "application/problem+json" : "application/json";
        Map<String, String> cabeceras = caso.get("cabeceras") == null ? Map.of() : (Map<String, String>) caso.get("cabeceras");
        try (Banco b = new Banco(Banco.LLAVE_TEST, x -> x.maxRetries(0),
                new Banco.Resp(estado, new Gson().toJson(caso.get("cuerpo")).getBytes(StandardCharsets.UTF_8), tipo, cabeceras, false))) {
            Map<String, Object> e = (Map<String, Object>) caso.get("esperado");
            if (estado < 400) {
                if ("listInvoices".equals(caso.get("operacion"))) {
                    InvoiceList l = b.trevol.invoices().listInvoices().execute();
                    assertEquals(((Number) e.get("cantidad")).intValue(), l.getData().size());
                    assertEquals(e.get("has_more"), l.getHasMore());
                } else {
                    Document d = b.trevol.invoices().retrieveInvoice("inv_x").execute();
                    assertEquals(e.get("id"), d.getId());
                    assertEquals(e.get("status"), d.getStatus());
                }
                return;
            }
            TrevolException x = assertThrows(TrevolException.class, () -> b.trevol.invoices().retrieveInvoice("inv_x").execute());
            assertEquals("TrevolError".equals(e.get("clase")) ? "TrevolException" : ((String) e.get("clase")).replace("Error", "Exception"), x.getClass().getSimpleName());
            assertEquals(e.get("code"), x.getCode());
            assertEquals(((Number) e.get("status")).intValue(), x.getStatus());
            if (e.containsKey("requestId")) assertEquals(e.get("requestId"), x.getRequestId());
        }
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest(name = "{0}")
    @MethodSource("eventos")
    void eventosDelFuturo(Map<String, Object> caso) throws Exception {
        byte[] clave = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        String secreto = "whsec_" + Base64.getEncoder().encodeToString(clave);
        long ts = 1793491200L;
        Map<String, Object> evento = (Map<String, Object>) caso.get("evento");
        String cuerpo = new Gson().toJson(evento);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clave, "HmacSHA256"));
        String firma = "v1," + Base64.getEncoder().encodeToString(mac.doFinal((evento.get("id") + "." + ts + "." + cuerpo).getBytes(StandardCharsets.UTF_8)));
        Webhook wh = new Webhook(secreto, () -> ts);
        Event e = wh.verify(cuerpo, Map.of("webhook-id", (String) evento.get("id"), "webhook-timestamp", Long.toString(ts), "webhook-signature", firma));
        Map<String, Object> esperado = (Map<String, Object>) caso.get("esperado");
        assertEquals(esperado.get("type"), e.getType());
        assertEquals(esperado.get("tieneModelo"), wh.eventData(e) != null);
        assertEquals(((Map<String, Object>) evento.get("data")).size(), e.getData().size());
    }
}
