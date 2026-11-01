package si.trevol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import si.trevol.generado.ApiException;

class ErroresTest {
    static final Map<String, Integer> ESTADO = Map.ofEntries(Map.entry("AuthenticationError", 401), Map.entry("PermissionDeniedError", 403),
            Map.entry("NotFoundError", 404), Map.entry("InvalidRequestError", 400), Map.entry("IdempotencyError", 409), Map.entry("ValidationError", 422),
            Map.entry("ConflictError", 409), Map.entry("PreconditionError", 422), Map.entry("DianRejectedError", 422), Map.entry("BalanceError", 402),
            Map.entry("RateLimitError", 429), Map.entry("DianUnavailableError", 503), Map.entry("InternalServerError", 500));

    @SuppressWarnings("unchecked")
    static Stream<Arguments> codes() {
        List<Arguments> todos = new ArrayList<>();
        Map<String, List<String>> clases = (Map<String, List<String>>) Banco.leerMapa("errores.json").get("clases");
        clases.forEach((clase, codes) -> codes.forEach(code -> todos.add(Arguments.of(clase, code))));
        return todos.stream();
    }

    @Test
    void cubreLos30Code() {
        assertEquals(30, codes().count());
    }

    @ParameterizedTest(name = "{1} → {0}")
    @MethodSource("codes")
    void cadaCodeVaASuClaseATravesDeUnMetodoGenerado(String clase, String code) throws Exception {
        try (Banco b = new Banco(Banco.LLAVE_TEST, x -> x.maxRetries(0), Banco.problema(ESTADO.get(clase), code))) {
            TrevolException e = assertThrows(TrevolException.class, () -> b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute());
            assertEquals("si.trevol." + clase.replace("Error", "Exception"), e.getClass().getName());
            assertEquals(code, e.getCode());
            assertEquals(ESTADO.get(clase), e.getStatus());
            assertEquals("req_c3c3c3c3c3c3c3c3c3c3c3c3", e.getRequestId());
            assertEquals("https://platform.trevol.si/docs/errores#" + code, e.getType());
            assertEquals(code, e.getProblem().get("code"));
            assertInstanceOf(RuntimeException.class, e);
        }
    }

    @Test
    void elErrorNoSeEnvuelveEnApiExceptionDelCodigoGenerado() throws Exception {
        try (Banco b = new Banco(Banco.problema(404, "not_found"))) {
            RuntimeException e = assertThrows(RuntimeException.class, () -> b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute());
            assertInstanceOf(NotFoundException.class, e);
            assertFalse(e instanceof Exception && e.getClass().getSuperclass() == ApiException.class);
        }
    }

    @Test
    void unCodeDesconocidoEsLaClaseBaseYNoRompe() throws Exception {
        try (Banco b = new Banco(Banco.problema(422, "code_del_futuro"))) {
            TrevolException e = assertThrows(TrevolException.class, () -> b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute());
            assertEquals(TrevolException.class, e.getClass());
            assertEquals("code_del_futuro", e.getCode());
        }
    }

    static Stream<Arguments> noJson() {
        return Stream.of(Arguments.of(502, InternalServerException.class), Arguments.of(404, NotFoundException.class),
                Arguments.of(409, ConflictException.class), Arguments.of(401, AuthenticationException.class));
    }

    @ParameterizedTest
    @MethodSource("noJson")
    void unaRespuestaQueNoEsJsonDaLaClasePorEstadoConCodeNullYElCuerpoRecortado(int estado, Class<? extends TrevolException> clase) throws Exception {
        String html = "<html>" + "x".repeat(5000) + "</html>";
        try (Banco b = new Banco(Banco.LLAVE_TEST, x -> x.maxRetries(0), Banco.crudo(estado, html.getBytes(StandardCharsets.UTF_8), "text/html"))) {
            TrevolException e = assertThrows(clase, () -> b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute());
            assertNull(e.getCode());
            assertEquals(2048, e.getDetail().length());
        }
    }

    @Test
    void tomaElRequestIdDeLaCabeceraCuandoElCuerpoNoLoTrae() throws Exception {
        String cuerpo = "{\"type\":\"x\",\"title\":\"t\",\"status\":404,\"code\":\"not_found\",\"retryable\":false}";
        try (Banco b = new Banco(new Banco.Resp(404, cuerpo.getBytes(StandardCharsets.UTF_8), "application/problem+json", Map.of("Request-Id", "req_d4d4d4d4d4d4d4d4d4d4d4d4"), false))) {
            NotFoundException e = assertThrows(NotFoundException.class, () -> b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute());
            assertEquals("req_d4d4d4d4d4d4d4d4d4d4d4d4", e.getRequestId());
        }
    }

    @Test
    void validationExceptionTraeErrorsConFieldPointerYMessage() throws Exception {
        try (Banco b = new Banco(Banco.problema(422, "validation_error", "{\"errors\":[{\"field\":\"items\",\"pointer\":\"/items\",\"message\":\"Falta\"}]}", null))) {
            ValidationException e = assertThrows(ValidationException.class, () -> b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute());
            TrevolException.FieldError x = e.getErrors().get(0);
            assertEquals(List.of("items", "/items", "Falta"), List.of(x.getField(), x.getPointer(), x.getMessage()));
        }
    }

    @Test
    void losCamposExtraQuedanEnProblem() throws Exception {
        try (Banco b = new Banco(Banco.problema(409, "external_id_in_use", "{\"existing_id\":\"inv_x\"}", null))) {
            ConflictException e = assertThrows(ConflictException.class, () -> b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute());
            assertEquals("inv_x", e.getProblem().get("existing_id"));
        }
    }

    @Test
    void laLlaveNoApareceEnToStringMensajeNiRastroDeNingunError() throws Exception {
        try (Banco b = new Banco(Banco.problema(401, "invalid_api_key"))) {
            TrevolException e = assertThrows(TrevolException.class, () -> b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute());
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            for (String t : new String[] {e.toString(), e.getMessage(), sw.toString(), b.trevol.toString(), String.valueOf(e.getProblem())}) {
                assertFalse(t.contains(Banco.LLAVE_TEST) || t.contains("a1".repeat(32)), t);
            }
            assertTrue(e.toString().contains("invalid_api_key") && e.toString().contains("HTTP 401") && e.toString().contains("req_"));
        }
    }

    @Test
    void unFalloDeRedEsApiConnectionExceptionConLaClaveYSeReintentaSoloSiEsSeguro() throws Exception {
        try (Banco b = new Banco(Banco.cortar())) {
            ApiConnectionException e = assertThrows(ApiConnectionException.class, () -> b.trevol.invoices().createInvoice(IdempotenciaTest.entrada()).execute());
            assertNotNull(e.getIdempotencyKey());
            assertEquals(2, b.esperas.size()); // con clave: 3 intentos (2 esperas entre ellos)
        }
        try (Banco b2 = new Banco(Banco.cortar())) {
            assertThrows(ApiConnectionException.class, () -> b2.trevol.webhookEndpoints().createWebhookEndpoint(
                    si.trevol.generado.model.WebhookEndpointInput.fromJson("{\"url\":\"https://tu-servidor.co/h\",\"events\":[\"*\"]}")).execute());
            assertEquals(0, b2.esperas.size()); // sin clave: un solo intento (ninguna espera)
        }
    }

    @Test
    void un429ConRetryAfterMayorQueElMaximoNoEsperaYLanzaElErrorConRetryAfter() throws Exception {
        try (Banco b = new Banco(Banco.problema(429, "rate_limited", "{\"retryable\":true}", Map.of("Retry-After", "120")))) {
            RateLimitException e = assertThrows(RateLimitException.class, () -> b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute());
            assertEquals(120.0, e.getRetryAfter());
            assertEquals(0, b.esperas.size());
            assertEquals(1, b.peticiones.size());
        }
    }

    @Test
    void un202EsUnResultadoNoUnErrorNiUnReintento() throws Exception {
        try (Banco b = new Banco(Banco.json(202, "{\"id\":\"" + Banco.ID_DOC + "\",\"object\":\"invoice\",\"status\":\"processing\"}"))) {
            var doc = b.trevol.invoices().createInvoice(IdempotenciaTest.entrada()).execute();
            assertEquals("processing", doc.getStatus());
            assertEquals(1, b.peticiones.size());
        }
    }
}
