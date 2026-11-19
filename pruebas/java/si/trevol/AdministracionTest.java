package si.trevol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import si.trevol.generado.Tablas;
import si.trevol.generado.model.AccountApiKey;
import si.trevol.generado.model.AccountApiKeyInput;
import si.trevol.generado.model.AccountApiKeyList;
import si.trevol.generado.model.AccountCompany;
import si.trevol.generado.model.AccountCompanyInput;
import si.trevol.generado.model.AccountCompanyList;
import si.trevol.generado.model.AccountDianDocument;
import si.trevol.generado.model.AccountDianDocumentList;
import si.trevol.generado.model.AccountRadianEvent;
import si.trevol.generado.model.AccountRadianEventList;
import si.trevol.generado.model.AccountRequestLog;
import si.trevol.generado.model.AccountRequestLogList;
import si.trevol.generado.model.InvoiceInput;

/** La llave de administración (tva_): formato, tipo, empresa por cliente y por petición, cabeceras, recurso account() y paginación. */
class AdministracionTest {
    static final String HEX_TVA = "c3".repeat(32);
    static final String TVA_TEST = "tva_test_" + HEX_TVA;
    static final String TVA_LIVE = "tva_live_" + "d4".repeat(32);
    static final String EMPRESA = "com_3f0c9a1e5b2d4c7f8a6e1d2c3b4a5f60";
    static final String OTRA = "com_8d4b2f6a1c3e4a5b9d7f2e4c6a8b0d1f";
    static final String EMPRESA_CAB = "Trevol-Company";
    static final String ENTORNO_CAB = "Trevol-Environment";

    /** El ejemplo de la respuesta 2xx de una operación, tal como lo trae la especificación compartida. */
    @SuppressWarnings("unchecked")
    static String ejemplo(String ruta, String metodo) {
        Map<String, Object> d = Banco.leerMapa("openapi.json");
        Map<String, Object> op = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) d.get("paths")).get(ruta)).get(metodo);
        for (Map.Entry<String, Object> r : ((Map<String, Object>) op.get("responses")).entrySet()) {
            if (!r.getKey().startsWith("2")) continue;
            Map<String, Object> json = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) r.getValue()).get("content")).get("application/json");
            return Banco.GSON.toJson(json.get("example"));
        }
        throw new IllegalStateException("sin ejemplo: " + metodo + " " + ruta);
    }

    /** Una página: el ejemplo de la lista con sus datos cambiados por {@code ids} (con el resto de los campos del primer elemento). */
    static String pagina(String ruta, boolean hayMas, String siguiente, String... ids) {
        Map<String, Object> l = Banco.GSON.fromJson(ejemplo(ruta, "get"), new TypeToken<Map<String, Object>>() {}.getType());
        @SuppressWarnings("unchecked")
        Map<String, Object> modelo = ((List<Map<String, Object>>) l.get("data")).get(0);
        List<Map<String, Object>> datos = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> x = new java.util.LinkedHashMap<>(modelo);
            x.put("id", id);
            datos.add(x);
        }
        l.put("data", datos);
        l.put("has_more", hayMas);
        l.put("next_cursor", siguiente);
        return Banco.GSON.toJson(l);
    }

    static boolean sinCabecerasDeEmpresa(Banco.Pet p) {
        return p.cabeceras.get(EMPRESA_CAB) == null && p.cabeceras.get(ENTORNO_CAB) == null;
    }

    // --- La llave ---------------------------------------------------------------------------------------------------------------------------------

    @Test
    void tvaTestYTvaLiveSonLlavesDeAdministracionConSuEntorno() {
        Trevol t = Trevol.builder().apiKey(TVA_TEST).build();
        assertEquals(Trevol.KeyType.ACCOUNT, t.keyType());
        assertEquals(Trevol.Environment.TEST, t.environment());
        assertNull(t.company());
        Trevol l = Trevol.builder().apiKey(TVA_LIVE).build();
        assertEquals(Trevol.KeyType.ACCOUNT, l.keyType());
        assertEquals(Trevol.Environment.LIVE, l.environment());
        assertEquals("account", l.keyType().toString());
        Trevol api = Trevol.builder().apiKey(Banco.LLAVE_TEST).build();
        assertEquals(Trevol.KeyType.API, api.keyType());
        assertEquals("api", api.keyType().toString());
        assertNull(api.company());
    }

    @Test
    void unaTvaMalFormadaEsConfigurationExceptionSinLaLlaveEnElMensaje() {
        List<String> malas = List.of("tva_test_abc", "tva_prod_" + HEX_TVA, "tva_" + HEX_TVA, "tvx_test_" + HEX_TVA, "TVA_TEST_" + HEX_TVA,
                "tva_test_" + "C3".repeat(32), "tva_test_" + HEX_TVA + "c3", "tva_live_" + "zz".repeat(32), "tva_test_" + HEX_TVA.substring(2));
        for (String mala : malas) {
            ConfigurationException e = assertThrows(ConfigurationException.class, () -> Trevol.builder().apiKey(mala).build(), mala);
            assertFalse(e.getMessage().contains(mala) || e.toString().contains(mala), mala);
            assertFalse(e.getMessage().contains(HEX_TVA.substring(0, 16)), mala);
            assertTrue(e.getMessage().contains("tva_test_") && e.getMessage().contains("tvl_live_"), e.getMessage());
            assertTrue(e.getMessage().contains("https://platform.trevol.si/llaves y la de administración en https://platform.trevol.si/administracion"),
                    e.getMessage());
        }
    }

    @Test
    void conEspaciosAlrededorLaTvaSirveYViajaSinEllos() throws Exception {
        try (Banco b = new Banco(" " + TVA_TEST + "\n", x -> x, Banco.json(200, ejemplo("/account", "get")))) {
            b.trevol.account().retrieveAccount().execute();
            assertEquals("Bearer " + TVA_TEST, b.peticiones.get(0).cabeceras.get("Authorization"));
        }
    }

    @Test
    void environmentEsSoloUnaAsercionTambienConUnaTva() {
        assertEquals(Trevol.Environment.LIVE, Trevol.builder().apiKey(TVA_LIVE).environment(Trevol.Environment.LIVE).build().environment());
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> Trevol.builder().apiKey(TVA_TEST).environment(Trevol.Environment.LIVE).build());
        assertTrue(e.getMessage().contains("tva_test_"), e.getMessage());
        assertFalse(e.getMessage().contains(HEX_TVA));
    }

    @Test
    void niToStringNiElTransporteMuestranLaTva() {
        Trevol t = Trevol.builder().apiKey(TVA_TEST).company(EMPRESA).build();
        assertFalse(t.toString().contains(HEX_TVA), t.toString());
        assertTrue(t.toString().contains("keyType=account") && t.toString().contains(EMPRESA), t.toString());
        assertFalse(t.withCompany(OTRA).toString().contains(HEX_TVA));
        Transporte tr = new Transporte(TVA_TEST, "x", "ua", 2, 60, "", s -> { }, () -> 1, () -> "u", java.time.Instant::now);
        assertFalse(tr.toString().contains(HEX_TVA));
        assertFalse(tr.conEmpresa(EMPRESA).toString().contains(HEX_TVA));
    }

    // --- La empresa: opción del cliente y withCompany ----------------------------------------------------------------------------------------

    @Test
    void companyConUnaTvlEsConfigurationException() {
        ConfigurationException e = assertThrows(ConfigurationException.class, () -> Trevol.builder().apiKey(Banco.LLAVE_TEST).company(EMPRESA).build());
        assertTrue(e.getMessage().contains("tva_") && e.getMessage().contains("tvl_"), e.getMessage());
        Trevol t = Trevol.builder().apiKey(Banco.LLAVE_LIVE).build();
        ConfigurationException w = assertThrows(ConfigurationException.class, () -> t.withCompany(EMPRESA));
        assertEquals(e.getMessage(), w.getMessage());
        assertFalse(w.getMessage().contains("b2".repeat(32)));
    }

    @Test
    void unaEmpresaMalFormadaEsConfigurationExceptionSinEcoDelValor() {
        Trevol t = Trevol.builder().apiKey(TVA_TEST).build();
        List<String> malas = new ArrayList<>(List.of("", "   ", "com_123", "COM_3F0C9A1E5B2D4C7F8A6E1D2C3B4A5F60", "com_3F0C9A1E5B2D4C7F8A6E1D2C3B4A5F60",
                "inv_3f0c9a1e5b2d4c7f8a6e1d2c3b4a5f60", EMPRESA + "0", "com_3f0c9a1e-5b2d-4c7f-8a6e-1d2c3b4a5f60", TVA_TEST));
        for (String mala : malas) {
            ConfigurationException e = assertThrows(ConfigurationException.class, () -> Trevol.builder().apiKey(TVA_TEST).company(mala).build(), mala);
            if (!mala.isBlank()) assertFalse(e.getMessage().contains(mala), mala);
            assertThrows(ConfigurationException.class, () -> t.withCompany(mala), mala);
        }
        assertThrows(ConfigurationException.class, () -> t.withCompany(null));
        assertEquals(EMPRESA, Trevol.builder().apiKey(TVA_TEST).company("  " + EMPRESA + " ").build().company());
    }

    @Test
    void conTvaYEmpresaLasRutasDeUnaEmpresaLlevanTrevolCompanyYTrevolEnvironment() throws Exception {
        try (Banco b = new Banco(TVA_TEST, x -> x.company(EMPRESA), Banco.json(200, Banco.DOCUMENTO))) {
            assertEquals(EMPRESA, b.trevol.company());
            b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute();
            okhttp3.Headers c = b.peticiones.get(0).cabeceras;
            assertEquals("Bearer " + TVA_TEST, c.get("Authorization"));
            assertEquals(EMPRESA, c.get(EMPRESA_CAB));
            assertEquals("test", c.get(ENTORNO_CAB));
            assertEquals("2026-11-18", c.get("Trevol-Version"));
            assertEquals(1, c.values(EMPRESA_CAB).size());
        }
        try (Banco b = new Banco(TVA_LIVE, x -> x.company(EMPRESA), Banco.json(201, Banco.DOCUMENTO))) {
            b.trevol.invoices().createInvoice(InvoiceInput.fromJson(Banco.ENTRADA_FACTURA)).execute();
            okhttp3.Headers c = b.peticiones.get(0).cabeceras;
            assertEquals(EMPRESA, c.get(EMPRESA_CAB));
            assertEquals("live", c.get(ENTORNO_CAB));
            assertNotNull(c.get("Idempotency-Key"));
        }
    }

    @Test
    void unaTvaSinEmpresaEnUnaRutaDeEmpresaFallaAntesDeMandarNada() throws Exception {
        try (Banco b = new Banco(TVA_TEST, x -> x, Banco.json(200, Banco.DOCUMENTO))) {
            ConfigurationException e = assertThrows(ConfigurationException.class, () -> b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute());
            assertTrue(e.getMessage().contains("withCompany") && e.getMessage().contains("company("), e.getMessage());
            assertFalse(e.getMessage().contains(HEX_TVA));
            assertThrows(ConfigurationException.class, () -> b.trevol.invoices().createInvoice(InvoiceInput.fromJson(Banco.ENTRADA_FACTURA)).execute());
            assertThrows(ConfigurationException.class, () -> b.trevol.general().retrieveCompany().execute());
            assertEquals(0, b.peticiones.size());
            assertEquals(0, b.esperas.size());
        }
    }

    @Test
    void lasOperacionesDeLaCuentaYHealthNoNecesitanEmpresaNiLlevanSusCabeceras() throws Exception {
        try (Banco b = new Banco(TVA_TEST, x -> x, Banco.json(200, ejemplo("/account", "get")), Banco.json(200, "{\"status\":\"ok\"}"))) {
            var info = b.trevol.account().retrieveAccount().execute();
            assertEquals("account_key_info", info.getObject());
            assertEquals("/v1/account", b.peticiones.get(0).ruta);
            b.trevol.general().getHealth().execute();
            assertEquals("/v1/health", b.peticiones.get(1).ruta);
            assertTrue(b.peticiones.stream().allMatch(AdministracionTest::sinCabecerasDeEmpresa));
        }
        // Con empresa tampoco: /v1/account no lee esas cabeceras.
        try (Banco b = new Banco(TVA_TEST, x -> x.company(EMPRESA), Banco.json(200, ejemplo("/account/companies", "get")))) {
            b.trevol.account().listAccountCompanies().execute();
            b.trevol.account().retrieveAccountCompany(OTRA).execute();
            assertTrue(b.peticiones.stream().allMatch(AdministracionTest::sinCabecerasDeEmpresa));
        }
    }

    @Test
    void unaTvlNuncaMandaTrevolCompanyNiTrevolEnvironmentAunqueOtroInterceptorLasPonga() throws Exception {
        OkHttpClient intruso = new OkHttpClient.Builder().addInterceptor(c -> c.proceed(c.request().newBuilder()
                .header(EMPRESA_CAB, EMPRESA).header(ENTORNO_CAB, "live").build())).build();
        try (Banco b = new Banco(Banco.LLAVE_TEST, x -> x.httpClient(intruso), Banco.json(200, Banco.DOCUMENTO))) {
            b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute();
            assertEquals(1, b.peticiones.size());
            assertTrue(b.peticiones.stream().allMatch(AdministracionTest::sinCabecerasDeEmpresa));
        }
        // Una tva_ en la cuenta tampoco las deja pasar si otro las pone; en una ruta de empresa manda las suyas.
        try (Banco b = new Banco(TVA_TEST, x -> x.httpClient(intruso).company(OTRA), Banco.json(200, Banco.DOCUMENTO))) {
            b.trevol.account().retrieveAccount().execute();
            assertTrue(sinCabecerasDeEmpresa(b.peticiones.get(0)));
            b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute();
            assertEquals(OTRA, b.peticiones.get(1).cabeceras.get(EMPRESA_CAB));
            assertEquals("test", b.peticiones.get(1).cabeceras.get(ENTORNO_CAB));
        }
    }

    @Test
    void unaTvlEnLasOperacionesDeLaCuentaFallaAntesDeMandarNada() throws Exception {
        try (Banco b = new Banco(Banco.LLAVE_TEST, x -> x, Banco.json(200, ejemplo("/account", "get")))) {
            List<org.junit.jupiter.api.function.Executable> llamadas = List.of(
                    () -> b.trevol.account().retrieveAccount().execute(),
                    () -> b.trevol.account().listAccountCompanies().execute(),
                    () -> b.trevol.account().createAccountCompany(AccountCompanyInput.fromJson("{\"nit\":\"900373427\"}")).execute(),
                    () -> b.trevol.account().retrieveAccountCompany(EMPRESA).execute(),
                    () -> b.trevol.account().listAccountCompanyApiKeys(EMPRESA).execute(),
                    () -> b.trevol.account().createAccountCompanyApiKey(EMPRESA,
                            AccountApiKeyInput.fromJson("{\"name\":\"Facturación\",\"permissions\":\"read\"}")).execute(),
                    () -> b.trevol.account().revokeAccountCompanyApiKey(EMPRESA, "key_" + "e5".repeat(16)).execute(),
                    () -> b.trevol.account().listAccountDianDocuments().execute(),
                    () -> b.trevol.account().listAccountRadianEvents().execute(),
                    () -> b.trevol.account().listAccountRequests().execute());
            assertEquals(10, llamadas.size());
            assertEquals(10, Tablas.OPERACIONES.stream().filter(o -> "administracion".equals(o.llave)).count());
            for (var llamada : llamadas) {
                ConfigurationException e = assertThrows(ConfigurationException.class, llamada);
                assertTrue(e.getMessage().contains("tva_test_") && e.getMessage().contains("tvl_"), e.getMessage());
                assertFalse(e.getMessage().contains("a1".repeat(32)), e.getMessage());
            }
            // Con paginate tampoco: la primera página ya falla sin red.
            assertThrows(ConfigurationException.class, () -> b.trevol.paginate(c -> b.trevol.account().listAccountRequests().startingAfter(c).execute(),
                    AccountRequestLogList::getData).iterator().next());
            assertEquals(0, b.peticiones.size());
            assertEquals(0, b.esperas.size());
        }
    }

    @Test
    void withCompanyDaUnClienteDerivadoQueComparteConexionesYNoCambiaAlOriginal() throws Exception {
        try (Banco b = new Banco(TVA_TEST, x -> x.company(EMPRESA), Banco.json(200, Banco.DOCUMENTO))) {
            Trevol otra = b.trevol.withCompany(OTRA);
            assertEquals(OTRA, otra.company());
            assertEquals(EMPRESA, b.trevol.company());
            assertEquals(Trevol.KeyType.ACCOUNT, otra.keyType());
            assertEquals(b.trevol.environment(), otra.environment());
            assertEquals(b.trevol.apiVersion(), otra.apiVersion());
            assertSame(b.trevol.apiClient().getHttpClient().connectionPool(), otra.apiClient().getHttpClient().connectionPool());
            assertSame(b.trevol.apiClient().getHttpClient().dispatcher(), otra.apiClient().getHttpClient().dispatcher());

            otra.invoices().retrieveInvoice(Banco.ID_DOC).execute();
            b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute();
            b.trevol.withCompany(EMPRESA).withCompany(OTRA).invoices().retrieveInvoice(Banco.ID_DOC).execute();
            assertEquals(List.of(OTRA, EMPRESA, OTRA), b.peticiones.stream().map(p -> p.cabeceras.get(EMPRESA_CAB)).collect(Collectors.toList()));
            assertTrue(b.peticiones.stream().allMatch(p -> ("Bearer " + TVA_TEST).equals(p.cabeceras.get("Authorization"))));
        }
        // Sin empresa en el cliente, withCompany la pone; el original sigue sin empresa.
        try (Banco b = new Banco(TVA_LIVE, x -> x, Banco.json(200, Banco.DOCUMENTO))) {
            b.trevol.withCompany(EMPRESA).invoices().retrieveInvoice(Banco.ID_DOC).execute();
            assertEquals("live", b.peticiones.get(0).cabeceras.get(ENTORNO_CAB));
            assertThrows(ConfigurationException.class, () -> b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute());
            assertEquals(1, b.peticiones.size());
        }
    }

    @Test
    void elDerivadoConservaReintentosYLaMismaIdempotencyKey() throws Exception {
        try (Banco b = new Banco(TVA_TEST, x -> x, Banco.problema(503, "dian_unavailable"), Banco.json(201, Banco.DOCUMENTO))) {
            b.trevol.withCompany(EMPRESA).invoices().createInvoice(InvoiceInput.fromJson(Banco.ENTRADA_FACTURA)).execute();
            assertEquals(2, b.peticiones.size());
            assertEquals(b.peticiones.get(0).cabeceras.get("Idempotency-Key"), b.peticiones.get(1).cabeceras.get("Idempotency-Key"));
            assertEquals(EMPRESA, b.peticiones.get(1).cabeceras.get(EMPRESA_CAB));
            assertEquals(1, b.esperas.size());
        }
    }

    @Test
    void queLlaveAdmiteCadaRutaSaleDeLaTablaYLasRutasNuevasSeDeducen() {
        for (Tablas.Operacion o : Tablas.OPERACIONES) {
            String esperada = o.ruta.equals("/account") || o.ruta.startsWith("/account/") ? "administracion" : o.ruta.equals("/health") ? "ninguna" : "api_o_administracion";
            assertEquals(esperada, o.llave, o.operationId);
            assertEquals(o.llave, Transporte.llaveDe(o, o.ruta));
        }
        assertEquals(10, Tablas.OPERACIONES.stream().filter(o -> "administracion".equals(o.llave)).count());
        assertEquals("administracion", Transporte.llaveDe(null, "/account/algo_nuevo"));
        assertEquals("api_o_administracion", Transporte.llaveDe(null, "/accounting"));
        assertEquals("api_o_administracion", Transporte.llaveDe(null, "/algo_nuevo"));
        assertEquals("ninguna", Transporte.llaveDe(null, "/health"));
    }

    // --- El recurso account() ------------------------------------------------------------------------------------------------------------------

    @Test
    void lasDiezOperacionesDeLaCuentaEstanEnAccount() throws Exception {
        Trevol t = Trevol.builder().apiKey(TVA_TEST).build();
        assertNotNull(t.account());
        List<String> ops = Tablas.OPERACIONES.stream().filter(o -> "Account".equals(o.recurso)).map(o -> o.operationId).sorted().collect(Collectors.toList());
        assertEquals(List.of("createAccountCompany", "createAccountCompanyApiKey", "listAccountCompanies", "listAccountCompanyApiKeys", "listAccountDianDocuments",
                "listAccountRadianEvents", "listAccountRequests", "retrieveAccount", "retrieveAccountCompany", "revokeAccountCompanyApiKey"), ops);
        for (String op : ops) {
            boolean hay = false;
            for (java.lang.reflect.Method m : t.account().getClass().getMethods()) hay |= m.getName().equals(op);
            assertTrue(hay, op);
        }
    }

    @Test
    void crearUnaEmpresaLlevaIdempotencyKeyYLaRepiteEnLosReintentos() throws Exception {
        try (Banco b = new Banco(TVA_LIVE, x -> x, Banco.problema(500, "internal_error"), Banco.json(201, ejemplo("/account/companies", "post")))) {
            AccountCompanyInput entrada = AccountCompanyInput.fromJson("{\"nit\":\"901234567\",\"name\":\"FERRETERÍA EL ROBLE SAS\"}");
            AccountCompany c = b.trevol.account().createAccountCompany(entrada).execute();
            assertEquals("pending", c.getStatus());
            assertEquals(2, b.peticiones.size());
            String clave = b.peticiones.get(0).cabeceras.get("Idempotency-Key");
            assertNotNull(clave);
            assertEquals(clave, b.peticiones.get(1).cabeceras.get("Idempotency-Key"));
            assertEquals("POST", b.peticiones.get(0).metodo);
            assertEquals("/v1/account/companies", b.peticiones.get(0).ruta);
            assertTrue(b.peticiones.get(0).cuerpo.contains("\"nit\":\"901234567\""), b.peticiones.get(0).cuerpo);
            assertTrue(b.peticiones.stream().allMatch(AdministracionTest::sinCabecerasDeEmpresa));
        }
        try (Banco b = new Banco(TVA_LIVE, x -> x, Banco.json(201, ejemplo("/account/companies", "post")))) {
            b.trevol.account().createAccountCompany(AccountCompanyInput.fromJson("{\"nit\":\"901234567\"}")).idempotencyKey("ALTA-901234567").execute();
            assertEquals("ALTA-901234567", b.peticiones.get(0).cabeceras.get("Idempotency-Key"));
        }
    }

    @Test
    void crearYRevocarUnaLlaveDeEmpresa() throws Exception {
        try (Banco b = new Banco(TVA_TEST, x -> x, Banco.json(201, ejemplo("/account/companies/{id}/api_keys", "post")),
                Banco.json(200, ejemplo("/account/companies/{id}/api_keys/{key_id}", "delete")))) {
            AccountApiKey k = b.trevol.account().createAccountCompanyApiKey(EMPRESA,
                    AccountApiKeyInput.fromJson("{\"name\":\"ERP de la bodega\",\"permissions\":\"write\"}")).execute();
            assertTrue(k.getToken().startsWith("tvl_test_"));
            assertEquals("/v1/account/companies/" + EMPRESA + "/api_keys", b.peticiones.get(0).ruta);
            assertNotNull(b.peticiones.get(0).cabeceras.get("Idempotency-Key"));
            var r = b.trevol.account().revokeAccountCompanyApiKey(EMPRESA, k.getId()).execute();
            assertEquals("revoked", r.getStatus());
            assertEquals("DELETE", b.peticiones.get(1).metodo);
            assertEquals("/v1/account/companies/" + EMPRESA + "/api_keys/" + k.getId(), b.peticiones.get(1).ruta);
            assertNull(b.peticiones.get(1).cabeceras.get("Idempotency-Key"));
        }
    }

    /** Al repetirla con la misma Idempotency-Key la API devuelve la llave con token null: un reintento a ciegas perdería el token. */
    @Test
    void crearUnaLlaveDeEmpresaNoSeReintentaAnteUn5xxNiUnCorteYSiAnteUn429() throws Exception {
        Tablas.Operacion op = Tablas.OPERACIONES.stream().filter(o -> o.operationId.equals("createAccountCompanyApiKey")).findFirst().orElseThrow();
        assertTrue(op.idempotente);
        assertEquals("solo_429", op.reintento);
        AccountApiKeyInput entrada = AccountApiKeyInput.fromJson("{\"name\":\"ERP de la bodega\",\"permissions\":\"write\"}");
        try (Banco b = new Banco(TVA_TEST, x -> x, Banco.problema(503, "dian_unavailable", "{\"retryable\":true}", null))) {
            DianUnavailableException e = assertThrows(DianUnavailableException.class,
                    () -> b.trevol.account().createAccountCompanyApiKey(EMPRESA, entrada).execute());
            assertNotNull(e.getIdempotencyKey());
            assertEquals(1, b.peticiones.size());
            assertEquals(0, b.esperas.size());
        }
        try (Banco b = new Banco(TVA_TEST, x -> x, Banco.cortar())) {
            assertThrows(ApiConnectionException.class, () -> b.trevol.account().createAccountCompanyApiKey(EMPRESA, entrada).execute());
            assertEquals(1, b.peticiones.size());
            assertEquals(0, b.esperas.size());
        }
        try (Banco b = new Banco(TVA_TEST, x -> x,
                Banco.problema(429, "rate_limited", "{\"retryable\":true,\"limit\":\"api_keys\"}", Map.of("Retry-After", "2")),
                Banco.json(201, ejemplo("/account/companies/{id}/api_keys", "post")))) {
            AccountApiKey k = b.trevol.account().createAccountCompanyApiKey(EMPRESA, entrada).execute();
            assertTrue(k.getToken().startsWith("tvl_test_"));
            assertEquals(2, b.peticiones.size());
            assertEquals(List.of(2.0), b.esperas);
            assertEquals(b.peticiones.get(0).cabeceras.get("Idempotency-Key"), b.peticiones.get(1).cabeceras.get("Idempotency-Key"));
        }
    }

    @Test
    void revocarSoloSeReintentaCon429() throws Exception {
        try (Banco b = new Banco(TVA_TEST, x -> x, Banco.problema(503, "internal_error"))) {
            assertThrows(TrevolException.class, () -> b.trevol.account().revokeAccountCompanyApiKey(EMPRESA, "key_" + "1".repeat(32)).execute());
            assertEquals(1, b.peticiones.size());
        }
    }

    // --- Paginación de las listas nuevas --------------------------------------------------------------------------------------------------------

    @Test
    void paginaLasEmpresasDeLaCuenta() throws Exception {
        String ruta = "/account/companies";
        try (Banco b = new Banco(TVA_TEST, x -> x, Banco.json(200, pagina(ruta, true, EMPRESA, "com_" + "1".repeat(32), EMPRESA)),
                Banco.json(200, pagina(ruta, false, null, OTRA)))) {
            Trevol t = b.trevol;
            List<String> ids = new ArrayList<>();
            for (AccountCompany c : t.paginate(cur -> t.account().listAccountCompanies().limit(2).status("enabled").startingAfter(cur).execute(), AccountCompanyList::getData)) {
                ids.add(c.getId());
            }
            assertEquals(List.of("com_" + "1".repeat(32), EMPRESA, OTRA), ids);
            assertEquals("/v1/account/companies?limit=2&status=enabled", b.peticiones.get(0).ruta);
            assertEquals("/v1/account/companies?limit=2&starting_after=" + EMPRESA + "&status=enabled", b.peticiones.get(1).ruta);
        }
    }

    @Test
    void paginaLasLlavesDeUnaEmpresaSinNextCursorConElIdDelUltimo() throws Exception {
        String ruta = "/account/companies/{id}/api_keys";
        String k1 = "key_" + "1".repeat(32), k2 = "key_" + "2".repeat(32);
        try (Banco b = new Banco(TVA_TEST, x -> x, Banco.json(200, pagina(ruta, true, null, k1)), Banco.json(200, pagina(ruta, false, null, k2)))) {
            Trevol t = b.trevol;
            List<String> ids = new ArrayList<>();
            for (AccountApiKey k : t.paginate(cur -> t.account().listAccountCompanyApiKeys(EMPRESA).startingAfter(cur).execute(), AccountApiKeyList::getData)) ids.add(k.getId());
            assertEquals(List.of(k1, k2), ids);
            assertEquals("/v1/account/companies/" + EMPRESA + "/api_keys?starting_after=" + k1, b.peticiones.get(1).ruta);
        }
    }

    @Test
    void paginaDocumentosEventosYPeticionesDeLaCuenta() throws Exception {
        String d1 = "ddoc_" + "1".repeat(32), d2 = "ddoc_" + "2".repeat(32);
        try (Banco b = new Banco(TVA_TEST, x -> x, Banco.json(200, pagina("/account/dian_documents", true, d1, d1)),
                Banco.json(200, pagina("/account/dian_documents", false, null, d2)))) {
            Trevol t = b.trevol;
            List<String> ids = new ArrayList<>();
            for (AccountDianDocument d : t.paginate(cur -> t.account().listAccountDianDocuments().company(EMPRESA + "," + OTRA).direction("received")
                    .startingAfter(cur).execute(), AccountDianDocumentList::getData)) ids.add(d.getId());
            assertEquals(List.of(d1, d2), ids);
            String q = b.peticiones.get(1).ruta;
            assertTrue(q.startsWith("/v1/account/dian_documents?") && q.contains("starting_after=" + d1) && q.contains("direction=received"), q);
        }
        String r1 = "rev_" + "1".repeat(32);
        try (Banco b = new Banco(TVA_TEST, x -> x, Banco.json(200, pagina("/account/radian_events", false, null, r1)))) {
            Trevol t = b.trevol;
            List<String> ids = new ArrayList<>();
            for (AccountRadianEvent e : t.paginate(cur -> t.account().listAccountRadianEvents().code("030").startingAfter(cur).execute(), AccountRadianEventList::getData)) ids.add(e.getId());
            assertEquals(List.of(r1), ids);
        }
        String q1 = "req_" + "1".repeat(24), q2 = "req_" + "2".repeat(24);
        try (Banco b = new Banco(TVA_TEST, x -> x, Banco.json(200, pagina("/account/requests", true, q1, q1)), Banco.json(200, pagina("/account/requests", false, null, q2)))) {
            Trevol t = b.trevol;
            List<String> ids = new ArrayList<>();
            for (AccountRequestLog r : t.paginate(cur -> t.account().listAccountRequests().startingAfter(cur).execute(), AccountRequestLogList::getData)) ids.add(r.getId());
            assertEquals(List.of(q1, q2), ids);
            assertTrue(b.peticiones.stream().allMatch(AdministracionTest::sinCabecerasDeEmpresa));
        }
    }

    // --- Los errores nuevos --------------------------------------------------------------------------------------------------------------------

    @Test
    void companyExistsEsConflictConLaEmpresaYNoSeReintenta() throws Exception {
        String empresa = ejemplo("/account/companies", "post");
        try (Banco b = new Banco(TVA_LIVE, x -> x, Banco.problema(409, "company_exists", "{\"company\":" + empresa + "}", null))) {
            ConflictException e = assertThrows(ConflictException.class,
                    () -> b.trevol.account().createAccountCompany(AccountCompanyInput.fromJson("{\"nit\":\"901234567\"}")).execute());
            assertEquals("company_exists", e.getCode());
            assertEquals("com_8d4b2f6a1c3e4a5b9d7f2e4c6a8b0d1f", ((Map<?, ?>) e.getProblem().get("company")).get("id"));
            assertNotNull(e.getIdempotencyKey());
            assertEquals(1, b.peticiones.size());
            assertFalse(e.toString().contains("d4".repeat(32)) || e.getMessage().contains("d4".repeat(32)));
        }
    }

    @Test
    void keyLimitReachedEsConflict() throws Exception {
        try (Banco b = new Banco(TVA_TEST, x -> x, Banco.problema(409, "key_limit_reached"))) {
            assertThrows(ConflictException.class, () -> b.trevol.account().createAccountCompanyApiKey(EMPRESA,
                    AccountApiKeyInput.fromJson("{\"name\":\"ERP\",\"permissions\":\"read\"}")).execute());
            assertEquals(1, b.peticiones.size());
        }
    }

    @Test
    void companyLimitReachedEsRateLimitConRetryAfterYNoEsperaUnaHora() throws Exception {
        try (Banco b = new Banco(TVA_LIVE, x -> x, Banco.problema(429, "company_limit_reached", "{\"retryable\":true,\"retry_after\":3600,\"limit\":\"hour\"}",
                Map.of("Retry-After", "3600")))) {
            RateLimitException e = assertThrows(RateLimitException.class,
                    () -> b.trevol.account().createAccountCompany(AccountCompanyInput.fromJson("{\"nit\":\"901234567\"}")).execute());
            assertEquals("company_limit_reached", e.getCode());
            assertEquals(3600.0, e.getRetryAfter());
            assertEquals("hour", e.getProblem().get("limit"));
            assertEquals(1, b.peticiones.size());
            assertEquals(0, b.esperas.size());
        }
    }

    @Test
    void environmentMismatchEsPermissionDeniedYLaLlaveNoSaleEnElError() throws Exception {
        try (Banco b = new Banco(TVA_TEST, x -> x.company(EMPRESA), Banco.problema(403, "permission_denied", "{\"reason\":\"environment_mismatch\"}", null),
                Banco.problema(401, "invalid_api_key"))) {
            PermissionDeniedException e = assertThrows(PermissionDeniedException.class, () -> b.trevol.invoices().retrieveInvoice(Banco.ID_DOC).execute());
            assertEquals("environment_mismatch", e.getProblem().get("reason"));
            AuthenticationException a = assertThrows(AuthenticationException.class, () -> b.trevol.account().retrieveAccount().execute());
            for (TrevolException x : List.of(e, a)) {
                assertFalse(x.getMessage().contains(HEX_TVA) || x.toString().contains(HEX_TVA));
                assertFalse(x.getHeaders().toString().toLowerCase(Locale.ROOT).contains(HEX_TVA));
            }
        }
    }
}
