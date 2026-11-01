package si.trevol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import si.trevol.generado.Tablas;
import si.trevol.generado.model.InvoiceInput;

class ClienteTest {
    @Test
    void authorizationTrevolVersionFijaUserAgentYNingunaXTrevol() throws Exception {
        try (Banco b = new Banco(Banco.json(200, Banco.DOCUMENTO))) {
            b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute();
            okhttp3.Headers c = b.peticiones.get(0).cabeceras;
            assertEquals("Bearer " + Banco.LLAVE_TEST, c.get("Authorization"));
            assertEquals("2026-10-22", c.get("Trevol-Version"));
            assertEquals("2026-10-22", Tablas.API_VERSION);
            assertTrue(c.get("User-Agent").matches("trevol-java/" + java.util.regex.Pattern.quote(Trevol.VERSION) + " \\(java [\\w.+-]+; \\+https://platform\\.trevol\\.si/docs\\)"), c.get("User-Agent"));
            assertEquals("application/json, application/problem+json", c.get("Accept"));
            for (String k : c.names()) {
                String m = k.toLowerCase(Locale.ROOT);
                assertFalse(m.startsWith("x-trevol-") || m.equals("trevol-empresa") || m.equals("trevol-entorno") || m.equals("trevol-escenario"), k);
            }
        }
    }

    @Test
    void apiVersionEsConfigurableYViajaEnCadaPeticion() throws Exception {
        try (Banco b = new Banco(Banco.LLAVE_TEST, x -> x.apiVersion("2026-10-21"), Banco.json(200, Banco.DOCUMENTO))) {
            b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute();
            b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute();
            assertEquals(List.of("2026-10-21", "2026-10-21"), b.peticiones.stream().map(p -> p.cabeceras.get("Trevol-Version")).collect(Collectors.toList()));
        }
    }

    @Test
    void elAcceptDeUnaDescargaEsElDelArchivoYLosBytesLleganIntactos() throws Exception {
        byte[] binario = {37, 80, 68, 70, 0, (byte) 255, 1, 2};
        try (Banco b = new Banco(Banco.crudo(200, binario, "application/pdf"))) {
            File pdf = b.trevol.invoices().downloadInvoicePdf(Banco.ID_DOC).execute();
            assertEquals("application/pdf, application/problem+json", b.peticiones.get(0).cabeceras.get("Accept"));
            assertArrayEquals(binario, Files.readAllBytes(pdf.toPath()));
            assertEquals("/v1/invoices/" + Banco.ID_DOC + "/pdf", b.peticiones.get(0).ruta);
            pdf.delete();
        }
    }

    @Test
    void laLlaveLiveSeUsaTalCualYLoDiceElEntorno() throws Exception {
        try (Banco b = new Banco(Banco.LLAVE_LIVE, x -> x, Banco.json(200, Banco.DOCUMENTO))) {
            b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute();
            assertEquals("Bearer " + Banco.LLAVE_LIVE, b.peticiones.get(0).cabeceras.get("Authorization"));
            assertEquals(Trevol.Environment.LIVE, b.trevol.environment());
        }
    }

    @Test
    void tvlTestEsTestYTvlLiveEsLive() {
        assertEquals(Trevol.Environment.TEST, Trevol.builder().apiKey(Banco.LLAVE_TEST).build().environment());
        assertEquals(Trevol.Environment.LIVE, Trevol.builder().apiKey(Banco.LLAVE_LIVE).build().environment());
    }

    @Test
    void malFormadaEsConfigurationExceptionSinRedYSinLaLlaveEnElMensaje() {
        String a = "a1".repeat(32);
        List<String> malas = new ArrayList<>(List.of("tvl_test_abc", "tvl_prod_" + a, "sk_live_" + a, "tvl_test_" + "A1".repeat(32), "tvl_test_" + "a1".repeat(33), "tvl_test_" + "zz".repeat(32)));
        for (String mala : malas) {
            ConfigurationException e = assertThrows(ConfigurationException.class, () -> Trevol.builder().apiKey(mala).build());
            assertFalse(e.getMessage().contains(mala) || e.toString().contains(mala));
        }
        assertThrows(ConfigurationException.class, () -> Trevol.builder().apiKey("   ").build());
    }

    @Test
    void environmentQueNoCoincideEsConfigurationExceptionAntesDeLlamar() {
        ConfigurationException e = assertThrows(ConfigurationException.class, () -> Trevol.builder().apiKey(Banco.LLAVE_TEST).environment(Trevol.Environment.LIVE).build());
        assertTrue(e.getMessage().contains("live"));
        assertThrows(ConfigurationException.class, () -> Trevol.builder().apiKey(Banco.LLAVE_LIVE).environment(Trevol.Environment.TEST).build());
        assertEquals(Trevol.Environment.TEST, Trevol.builder().apiKey(Banco.LLAVE_TEST).environment(Trevol.Environment.TEST).build().environment());
    }

    @Test
    void niToStringNiElTransporteMuestranLaLlave() {
        Trevol t = Trevol.builder().apiKey(Banco.LLAVE_TEST).build();
        assertFalse(t.toString().contains("a1".repeat(32)));
        assertFalse(new Transporte(Banco.LLAVE_TEST, "x", "ua", 2, 60, "", s -> { }, () -> 1, () -> "u", java.time.Instant::now).toString().contains("a1".repeat(32)));
    }

    @Test
    void okhttpNoReintentaPorSuCuenta() throws Exception {
        // C14: el SDK es el único que reintenta; con maxRetries 0 una caída es UN intento (retryOnConnectionFailure(false)).
        try (Banco b = new Banco(Banco.LLAVE_TEST, x -> x.maxRetries(0), Banco.cortar())) {
            assertThrows(ApiConnectionException.class, () -> b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute());
            assertEquals(0, b.esperas.size());
        }
    }

    @Test
    void cadaEtiquetaDeOpenapiTieneSuMetodoEnElCliente() {
        @SuppressWarnings("unchecked")
        List<String> etiquetas = Banco.GSON.fromJson(leerTexto("tags.json"), List.class);
        assertEquals(etiquetas, Tablas.ETIQUETAS);
        for (String e : etiquetas) {
            String metodo = Character.toLowerCase(e.charAt(0)) + e.substring(1); // si la API suma un recurso, esto falla hasta agregarlo
            assertDoesNotThrow(() -> Trevol.class.getMethod(metodo), e);
        }
    }

    @Test
    void cadaOperationIdExisteComoMetodoEnSuRecurso() throws Exception {
        Trevol t = Trevol.builder().apiKey(Banco.LLAVE_TEST).build();
        for (Tablas.Operacion o : Tablas.OPERACIONES) {
            Object recurso = Trevol.class.getMethod(Character.toLowerCase(o.recurso.charAt(0)) + o.recurso.substring(1)).invoke(t);
            boolean hay = false;
            for (java.lang.reflect.Method m : recurso.getClass().getMethods()) hay |= m.getName().equals(o.operationId);
            assertTrue(hay, o.operationId);
        }
    }

    @Test
    void elJsonDeUnaFacturaSeLeeConLosAyudantes() throws Exception {
        InvoiceInput a = InvoiceInput.fromJson(Banco.ENTRADA_FACTURA);
        InvoiceInput b = TrevolJson.fromJson(Banco.ENTRADA_FACTURA, InvoiceInput.class);
        assertEquals(a, b);
        assertNotNull(TrevolJson.toJson(b));
    }

    private static String leerTexto(String n) {
        try {
            return Files.readString(Banco.datos(n));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertDoesNotThrow(org.junit.jupiter.api.function.Executable e, String msg) {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(e, msg);
    }
}
