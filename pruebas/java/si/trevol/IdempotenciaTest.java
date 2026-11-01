package si.trevol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.trevol.generado.model.InvoiceInput;
import si.trevol.generado.model.TopUpInput;
import si.trevol.generado.model.WebhookEndpointInput;

class IdempotenciaTest {
    static InvoiceInput entrada() throws Exception {
        return InvoiceInput.fromJson(Banco.ENTRADA_FACTURA);
    }

    @Test
    void generaUnaClaveYLaRepiteEnLosTresIntentos() throws Exception {
        try (Banco b = new Banco(Banco.problema(503, "dian_unavailable", "{\"retryable\":true}", null),
                Banco.problema(503, "dian_unavailable", "{\"retryable\":true}", null), Banco.json(201, Banco.DOCUMENTO))) {
            b.trevol.invoices().createInvoice(entrada()).execute();
            Set<String> claves = b.peticiones.stream().map(p -> p.cabeceras.get("Idempotency-Key")).collect(Collectors.toSet());
            assertEquals(3, b.peticiones.size());
            assertEquals(Set.of("00000000-0000-4000-8000-000000000001"), claves);
            assertEquals(1, b.peticiones.stream().map(p -> p.cuerpo).distinct().count()); // el cuerpo viaja entero en cada intento
            assertTrue(b.peticiones.get(2).cuerpo.replace(" ", "").contains("\"external_id\":\"VENTA-2026-000123\""));
            assertEquals(2, b.esperas.size());
        }
    }

    @Test
    void respetaLaClaveDeLaPersona() throws Exception {
        try (Banco b = new Banco(Banco.json(201, Banco.DOCUMENTO))) {
            b.trevol.invoices().createInvoice(entrada()).idempotencyKey("VENTA-2026-000123").execute();
            assertEquals("VENTA-2026-000123", b.peticiones.get(0).cabeceras.get("Idempotency-Key"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"con espacio", "tilde-é"})
    void unaClaveMalaSeRechazaLocalmenteSinRed(String mala) throws Exception {
        try (Banco b = new Banco(Banco.json(201, Banco.DOCUMENTO))) {
            assertThrows(ConfigurationException.class, () -> b.trevol.invoices().createInvoice(entrada()).idempotencyKey(mala).execute());
            assertEquals(0, b.peticiones.size());
        }
    }

    @Test
    void unaClaveLargaSeRechazaLocalmenteSinRed() throws Exception {
        try (Banco b = new Banco(Banco.json(201, Banco.DOCUMENTO))) {
            assertThrows(ConfigurationException.class, () -> b.trevol.invoices().createInvoice(entrada()).idempotencyKey("x".repeat(256)).execute());
            assertEquals(0, b.peticiones.size());
        }
    }

    @Test
    void laLlevaElErrorParaReintentarMasTardeConLaMisma() throws Exception {
        try (Banco b = new Banco(Banco.problema(422, "dian_rejected"))) {
            DianRejectedException e = assertThrows(DianRejectedException.class, () -> b.trevol.invoices().createInvoice(entrada()).execute());
            assertEquals("00000000-0000-4000-8000-000000000001", e.getIdempotencyKey());
        }
    }

    @Test
    void crearCorregirYLaRecargaLaLlevanElRestoNo() throws Exception {
        java.util.function.Function<Banco.Pet, Banco.Resp> segunLaRuta = p -> {
            if (p.ruta.endsWith("/top_ups")) return Banco.json(201, "{\"object\":\"top_up\",\"reference\":\"TRV-REC-1\",\"amount_cop\":50000,\"status\":\"prepared\"}");
            if (p.ruta.endsWith("/webhook_endpoints")) return Banco.json(201, "{\"id\":\"we_" + "a".repeat(32) + "\",\"object\":\"webhook_endpoint\",\"environment\":\"test\"}");
            if (p.metodo.equals("POST") && p.ruta.endsWith("/invoices")) return Banco.json(201, Banco.DOCUMENTO);
            return Banco.json(200, Banco.DOCUMENTO);
        };
        try (Banco b = new Banco(segunLaRuta)) {
            Trevol t = b.trevol;
            t.invoices().createInvoice(entrada()).execute();
            t.invoices().correctInvoice(Banco.ID_DOC, entrada()).execute();
            t.balance().createBalanceTopUp(new TopUpInput().amountCop(50000)).execute();
            t.invoices().retrieveInvoice(Banco.ID_DOC).execute();
            t.invoices().sendInvoice(Banco.ID_DOC).execute();
            t.webhookEndpoints().createWebhookEndpoint(WebhookEndpointInput.fromJson("{\"url\":\"https://tu-servidor.co/h\",\"events\":[\"*\"]}")).execute();
            t.invoices().previewInvoice(entrada()).execute();
            List<String> vistas = b.peticiones.stream()
                    .map(p -> p.metodo + " " + p.ruta.split("\\?")[0] + " " + (p.cabeceras.get("Idempotency-Key") != null ? "True" : "False")).collect(Collectors.toList());
            assertEquals(List.of("POST /v1/invoices True", "PUT /v1/invoices/" + Banco.ID_DOC + " True", "POST /v1/balance/top_ups True",
                    "GET /v1/invoices/" + Banco.ID_DOC + " False", "POST /v1/invoices/" + Banco.ID_DOC + "/send False",
                    "POST /v1/webhook_endpoints False", "POST /v1/invoices/preview False"), vistas);
        }
    }
}
