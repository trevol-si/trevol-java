package si.trevol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import si.trevol.generado.model.DocumentEventData;
import si.trevol.generado.model.Event;

class WebhookTest {
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> lista() {
        return (List<Map<String, Object>>) Banco.leerMapa("webhooks.json").get("casos");
    }

    static Stream<Map<String, Object>> casos() { return lista().stream(); }

    static Map<String, Object> ok() { return lista().stream().filter(c -> "valido".equals(c.get("nombre"))).findFirst().orElseThrow(); }

    @SuppressWarnings("unchecked")
    static Webhook en(Map<String, Object> c) { return new Webhook((String) c.get("secreto"), () -> ((Number) c.get("ahora")).longValue()); }

    @SuppressWarnings("unchecked")
    static Map<String, String> cab(Map<String, Object> c) { return (Map<String, String>) c.get("cabeceras"); }

    @Test
    void laToleranciaEsDe5Minutos() {
        assertEquals(300, ((Number) Banco.leerMapa("webhooks.json").get("tolerancia")).intValue());
        assertEquals(300, Webhook.TOLERANCIA);
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void vectoresDePruebasDatosWebhooksJson(Map<String, Object> caso) {
        Webhook wh = en(caso);
        if ((Boolean) caso.get("valido")) {
            Event e = wh.verify((String) caso.get("cuerpo"), cab(caso));
            Map<String, Object> esperado = (Map<String, Object>) caso.get("evento");
            assertEquals(esperado.get("id"), e.getId());
            assertEquals(esperado.get("type"), e.getType());
        } else {
            WebhookVerificationException x = assertThrows(WebhookVerificationException.class, () -> wh.verify((String) caso.get("cuerpo"), cab(caso)));
            assertEquals(caso.get("motivo"), x.getReason());
        }
    }

    @Test
    void bytesYCabecerasDeCualquierCajaConValoresEnLista() {
        Map<String, Object> ok = ok();
        Webhook wh = en(ok);
        Map<String, String> c = cab(ok);
        assertNotNull(wh.verify(((String) ok.get("cuerpo")).getBytes(StandardCharsets.UTF_8), c).getId());
        Map<String, List<String>> servlet = new HashMap<>();
        servlet.put("Webhook-Id", List.of(c.get("webhook-id"), "evt_otro"));
        servlet.put("WEBHOOK-TIMESTAMP", List.of(c.get("webhook-timestamp")));
        servlet.put("webhook-signature", List.of(c.get("webhook-signature")));
        assertNotNull(wh.verify((String) ok.get("cuerpo"), servlet).getId());
    }

    @Test
    void unCuerpoNuloOInvalidoUtf8SeRechazaDiciendoComoLeerElCrudo() {
        Map<String, Object> ok = ok();
        WebhookVerificationException e = assertThrows(WebhookVerificationException.class, () -> en(ok).verify((String) null, cab(ok)));
        assertTrue(e.getMessage().contains("CRUDO"));
        assertEquals("cuerpo", e.getReason());
        assertEquals("cuerpo", assertThrows(WebhookVerificationException.class, () -> en(ok).verify(new byte[] {(byte) 0xff, (byte) 0xfe}, cab(ok))).getReason());
    }

    @Test
    void unSecretoVacioOConBasuraEsConfigurationExceptionYNoSeFiltra() {
        for (String malo : new String[] {"", "   ", "whsec_"}) assertThrows(ConfigurationException.class, () -> new Webhook(malo));
        ConfigurationException e = assertThrows(ConfigurationException.class, () -> new Webhook("whsec_@@@@@"));
        assertFalse(e.getMessage().contains("@@@"));
    }

    @Test
    void eventDataDaElModeloSegunElTypeYUnTypeNuevoDaNull() {
        Map<String, Object> ok = ok();
        Webhook wh = en(ok);
        Event e = wh.verify((String) ok.get("cuerpo"), cab(ok));
        Object d = wh.eventData(e);
        assertInstanceOf(DocumentEventData.class, d);
        assertEquals("accepted", ((DocumentEventData) d).getStatus());
        e.setType("invoice.archived");
        assertNull(wh.eventData(e));
    }

    @Test
    void elMensajeDeUnRechazoNoIncluyeElSecreto() {
        Map<String, Object> ok = ok();
        WebhookVerificationException e = assertThrows(WebhookVerificationException.class, () -> en(ok).verify(ok.get("cuerpo") + " ", cab(ok)));
        assertFalse(e.getMessage().contains(((String) ok.get("secreto")).substring(6)));
    }
}
