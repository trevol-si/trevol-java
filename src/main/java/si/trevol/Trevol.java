package si.trevol;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.List;
import okhttp3.OkHttpClient;
import si.trevol.generado.ApiClient;
import si.trevol.generado.Tablas;
import si.trevol.generado.api.AccountApi;
import si.trevol.generado.api.BalanceApi;
import si.trevol.generado.api.CreditNotesApi;
import si.trevol.generado.api.DebitNotesApi;
import si.trevol.generado.api.EquivalentDocumentAdjustmentsApi;
import si.trevol.generado.api.EquivalentDocumentsApi;
import si.trevol.generado.api.EventsApi;
import si.trevol.generado.api.GeneralApi;
import si.trevol.generado.api.InvoicesApi;
import si.trevol.generado.api.PayrollAdjustmentsApi;
import si.trevol.generado.api.PayrollsApi;
import si.trevol.generado.api.RadianEventsApi;
import si.trevol.generado.api.RequestsApi;
import si.trevol.generado.api.SupportDocumentAdjustmentsApi;
import si.trevol.generado.api.SupportDocumentsApi;
import si.trevol.generado.api.WebhookEndpointsApi;
import si.trevol.generado.api.CustomersApi;
import si.trevol.generado.api.DianDocumentsApi;
import si.trevol.generado.api.DianSyncsApi;
import si.trevol.generado.api.InvoiceBatchesApi;
import si.trevol.generado.api.NumberingAssociationsApi;
import si.trevol.generado.api.NumberingRangesApi;
import si.trevol.generado.api.NumberingRequestsApi;
import si.trevol.generado.api.PaperNumberingRangesApi;
import si.trevol.generado.api.ProductsApi;
import si.trevol.generado.api.RadianStatusApi;
import si.trevol.generado.api.ReportRunsApi;
import si.trevol.generado.api.ReportsApi;

/**
 * El cliente de la API de Trevol: un método por recurso ({@code invoices()}, {@code creditNotes()}, …), cada uno con los métodos
 * generados de la especificación ({@code createInvoice}, {@code listInvoices}, {@code retrieveInvoice}…). Documentación:
 * https://platform.trevol.si/docs
 *
 * <pre>{@code
 * Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).build(); // tvl_test_… → trevol.environment() == TEST
 * }</pre>
 *
 * <p>Con una llave de administración ({@code tva_test_…} o {@code tva_live_…}), {@code account()} trae las operaciones de la cuenta y las
 * rutas de una empresa piden su id: {@code Trevol.builder().apiKey(llave).company("com_…")} o, por empresa, {@code trevol.withCompany("com_…")}.
 *
 * <p>Cada operación se arma por pasos y se ejecuta con {@code execute()}: {@code trevol.invoices().createInvoice(entrada).idempotencyKey("VENTA-1").execute()}.
 * Usa los métodos síncronos: una excepción del SDK en una llamada asíncrona de OkHttp llega envuelta en un {@code ApiException}.
 */
public final class Trevol {
    public static final String VERSION = Tablas.VERSION;
    public static final String API_VERSION = Tablas.API_VERSION;
    public static final String BASE_URL = "https://api.trevol.si/v1";

    /** {@code TEST} (llave tvl_test_ o tva_test_, habilitación de la DIAN) o {@code LIVE} (tvl_live_ o tva_live_, producción). */
    public enum Environment {
        TEST, LIVE;

        @Override
        public String toString() { return name().toLowerCase(Locale.ROOT); }
    }

    /** {@code API} (llave {@code tvl_…}, de una empresa) o {@code ACCOUNT} (llave de administración {@code tva_…}, de una cuenta). */
    public enum KeyType {
        API, ACCOUNT;

        @Override
        public String toString() { return name().toLowerCase(Locale.ROOT); }
    }

    private final Environment environment;
    private final KeyType keyType;
    private final String company;
    private final String apiVersion;
    private final String base;
    private final OkHttpClient sinTransporte;
    private final Transporte transporte;
    private final ApiClient apiClient;
    private final AccountApi account;
    private final BalanceApi balance;
    private final CreditNotesApi creditNotes;
    private final DebitNotesApi debitNotes;
    private final EquivalentDocumentAdjustmentsApi equivalentDocumentAdjustments;
    private final EquivalentDocumentsApi equivalentDocuments;
    private final EventsApi events;
    private final GeneralApi general;
    private final InvoicesApi invoices;
    private final PayrollAdjustmentsApi payrollAdjustments;
    private final PayrollsApi payrolls;
    private final RadianEventsApi radianEvents;
    private final RequestsApi requests;
    private final SupportDocumentAdjustmentsApi supportDocumentAdjustments;
    private final SupportDocumentsApi supportDocuments;
    private final WebhookEndpointsApi webhookEndpoints;
    private final CustomersApi customers;
    private final DianDocumentsApi dianDocuments;
    private final DianSyncsApi dianSyncs;
    private final InvoiceBatchesApi invoiceBatches;
    private final NumberingAssociationsApi numberingAssociations;
    private final NumberingRangesApi numberingRanges;
    private final NumberingRequestsApi numberingRequests;
    private final PaperNumberingRangesApi paperNumberingRanges;
    private final ProductsApi products;
    private final RadianStatusApi radianStatus;
    private final ReportRunsApi reportRuns;
    private final ReportsApi reports;

    private Trevol(Builder b) {
        String llave = b.apiKey != null ? b.apiKey : System.getenv("TREVOL_API_KEY");
        if (llave == null || llave.trim().isEmpty()) {
            throw new ConfigurationException("Falta la llave de la API: pásala a Trevol.builder().apiKey(...) o define la variable de entorno TREVOL_API_KEY.");
        }
        Llave.Datos datos = Llave.leer(llave);
        this.environment = datos.entorno;
        this.keyType = datos.tipo;
        if (b.environment != null && b.environment != environment) {
            String prefijo = keyType == KeyType.ACCOUNT ? "tva_" : "tvl_";
            throw new ConfigurationException("Pediste el entorno «" + b.environment + "» pero la llave es de «" + environment + "» ("
                    + prefijo + (environment == Environment.TEST ? "test_ = habilitación" : "live_ = producción") + ").");
        }
        this.company = b.company == null ? null : empresaValida(keyType, b.company);
        if (b.maxRetries < 0) throw new ConfigurationException("maxRetries no puede ser negativo.");
        this.apiVersion = b.apiVersion != null ? b.apiVersion : API_VERSION;
        this.base = b.baseUrl.endsWith("/") ? b.baseUrl.substring(0, b.baseUrl.length() - 1) : b.baseUrl;
        String ruta = java.net.URI.create(base).getRawPath();
        Transporte t = new Transporte(llave.trim(), apiVersion,
                "trevol-java/" + VERSION + " (java " + System.getProperty("java.version") + "; +https://platform.trevol.si/docs)",
                b.maxRetries, b.maxRetryAfter.toMillis() / 1000.0, ruta == null ? "" : ruta,
                b.dormir != null ? b.dormir : s -> dormir(s), b.azar != null ? b.azar : Math::random,
                b.uuid != null ? b.uuid : () -> java.util.UUID.randomUUID().toString(), b.ahora != null ? b.ahora : Instant::now);
        this.transporte = company == null ? t : t.conEmpresa(company);
        OkHttpClient.Builder http = (b.httpClient != null ? b.httpClient.newBuilder() : new OkHttpClient.Builder())
                // C14: el SDK es el único que reintenta (OkHttp reintenta en silencio fallos de conexión, también de un POST sin clave).
                .retryOnConnectionFailure(false);
        if (b.httpClient == null) {
            http.connectTimeout(b.timeout).readTimeout(b.timeout).writeTimeout(b.timeout);
        }
        this.sinTransporte = http.build();
        this.apiClient = new ClienteApi(sinTransporte.newBuilder().addInterceptor(transporte).build());
        apiClient.setBasePath(base);
        this.account = new AccountApi(apiClient);
        this.balance = new BalanceApi(apiClient);
        this.creditNotes = new CreditNotesApi(apiClient);
        this.debitNotes = new DebitNotesApi(apiClient);
        this.equivalentDocumentAdjustments = new EquivalentDocumentAdjustmentsApi(apiClient);
        this.equivalentDocuments = new EquivalentDocumentsApi(apiClient);
        this.events = new EventsApi(apiClient);
        this.general = new GeneralApi(apiClient);
        this.invoices = new InvoicesApi(apiClient);
        this.payrollAdjustments = new PayrollAdjustmentsApi(apiClient);
        this.payrolls = new PayrollsApi(apiClient);
        this.radianEvents = new RadianEventsApi(apiClient);
        this.requests = new RequestsApi(apiClient);
        this.supportDocumentAdjustments = new SupportDocumentAdjustmentsApi(apiClient);
        this.supportDocuments = new SupportDocumentsApi(apiClient);
        this.webhookEndpoints = new WebhookEndpointsApi(apiClient);
        this.customers = new CustomersApi(apiClient);
        this.dianDocuments = new DianDocumentsApi(apiClient);
        this.dianSyncs = new DianSyncsApi(apiClient);
        this.invoiceBatches = new InvoiceBatchesApi(apiClient);
        this.numberingAssociations = new NumberingAssociationsApi(apiClient);
        this.numberingRanges = new NumberingRangesApi(apiClient);
        this.numberingRequests = new NumberingRequestsApi(apiClient);
        this.paperNumberingRanges = new PaperNumberingRangesApi(apiClient);
        this.products = new ProductsApi(apiClient);
        this.radianStatus = new RadianStatusApi(apiClient);
        this.reportRuns = new ReportRunsApi(apiClient);
        this.reports = new ReportsApi(apiClient);
    }

    /** El cliente derivado de {@link #withCompany}: la misma llave, las mismas opciones y las mismas conexiones; otra empresa. */
    private Trevol(Trevol padre, String company) {
        this.environment = padre.environment;
        this.keyType = padre.keyType;
        this.company = company;
        this.apiVersion = padre.apiVersion;
        this.base = padre.base;
        this.sinTransporte = padre.sinTransporte;
        this.transporte = padre.transporte.conEmpresa(company);
        // newBuilder() comparte el pool de conexiones y el despachador de OkHttp: el derivado no abre conexiones nuevas.
        this.apiClient = new ClienteApi(sinTransporte.newBuilder().addInterceptor(transporte).build());
        apiClient.setBasePath(base);
        this.account = new AccountApi(apiClient);
        this.balance = new BalanceApi(apiClient);
        this.creditNotes = new CreditNotesApi(apiClient);
        this.debitNotes = new DebitNotesApi(apiClient);
        this.equivalentDocumentAdjustments = new EquivalentDocumentAdjustmentsApi(apiClient);
        this.equivalentDocuments = new EquivalentDocumentsApi(apiClient);
        this.events = new EventsApi(apiClient);
        this.general = new GeneralApi(apiClient);
        this.invoices = new InvoicesApi(apiClient);
        this.payrollAdjustments = new PayrollAdjustmentsApi(apiClient);
        this.payrolls = new PayrollsApi(apiClient);
        this.radianEvents = new RadianEventsApi(apiClient);
        this.requests = new RequestsApi(apiClient);
        this.supportDocumentAdjustments = new SupportDocumentAdjustmentsApi(apiClient);
        this.supportDocuments = new SupportDocumentsApi(apiClient);
        this.webhookEndpoints = new WebhookEndpointsApi(apiClient);
        this.customers = new CustomersApi(apiClient);
        this.dianDocuments = new DianDocumentsApi(apiClient);
        this.dianSyncs = new DianSyncsApi(apiClient);
        this.invoiceBatches = new InvoiceBatchesApi(apiClient);
        this.numberingAssociations = new NumberingAssociationsApi(apiClient);
        this.numberingRanges = new NumberingRangesApi(apiClient);
        this.numberingRequests = new NumberingRequestsApi(apiClient);
        this.paperNumberingRanges = new PaperNumberingRangesApi(apiClient);
        this.products = new ProductsApi(apiClient);
        this.radianStatus = new RadianStatusApi(apiClient);
        this.reportRuns = new ReportRunsApi(apiClient);
        this.reports = new ReportsApi(apiClient);
    }

    private static String empresaValida(KeyType tipo, String company) {
        if (tipo != KeyType.ACCOUNT) {
            throw new ConfigurationException("company solo aplica a una llave de administración (tva_…): una llave tvl_ ya es de una empresa.");
        }
        return Llave.empresa(company);
    }

    private static void dormir(double segundos) {
        try {
            Thread.sleep((long) Math.round(segundos * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiConnectionException("Se interrumpió la espera entre reintentos.", null, e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** {@code TEST} o {@code LIVE}, deducido del prefijo de la llave (también con una tva_). */
    public Environment environment() { return environment; }
    /** {@code API} con una llave {@code tvl_…}; {@code ACCOUNT} con una llave de administración {@code tva_…}. */
    public KeyType keyType() { return keyType; }
    /** La empresa ({@code com_…}) a la que van las rutas de una empresa con una llave de administración; null si no hay. */
    public String company() { return company; }
    public String apiVersion() { return apiVersion; }

    /**
     * Un cliente para otra empresa de la cuenta, con la misma llave de administración, las mismas opciones y las mismas conexiones (es barato:
     * créalo por petición si hace falta). Este cliente no cambia. Con una llave {@code tvl_}, {@link ConfigurationException}.
     *
     * <pre>{@code
     * Document f = trevol.withCompany("com_…").invoices().retrieveInvoice("inv_…").execute();
     * }</pre>
     */
    public Trevol withCompany(String company) {
        return new Trevol(this, empresaValida(keyType, company));
    }
    /** El cliente generado, por si necesitas algo que la capa no expone. */
    public ApiClient apiClient() { return apiClient; }

    /**
     * Las operaciones de {@code /v1/account}: solo con una llave de administración ({@code tva_test_…} o {@code tva_live_…}); con una
     * {@code tvl_}, cada una es una {@link ConfigurationException} antes de llamar.
     */
    public AccountApi account() { return account; }
    public BalanceApi balance() { return balance; }
    public CreditNotesApi creditNotes() { return creditNotes; }
    public DebitNotesApi debitNotes() { return debitNotes; }
    public EquivalentDocumentAdjustmentsApi equivalentDocumentAdjustments() { return equivalentDocumentAdjustments; }
    public EquivalentDocumentsApi equivalentDocuments() { return equivalentDocuments; }
    public EventsApi events() { return events; }
    public GeneralApi general() { return general; }
    public InvoicesApi invoices() { return invoices; }
    public PayrollAdjustmentsApi payrollAdjustments() { return payrollAdjustments; }
    public PayrollsApi payrolls() { return payrolls; }
    public RadianEventsApi radianEvents() { return radianEvents; }
    public RequestsApi requests() { return requests; }
    public SupportDocumentAdjustmentsApi supportDocumentAdjustments() { return supportDocumentAdjustments; }
    public SupportDocumentsApi supportDocuments() { return supportDocuments; }
    public WebhookEndpointsApi webhookEndpoints() { return webhookEndpoints; }
    public CustomersApi customers() { return customers; }
    public DianDocumentsApi dianDocuments() { return dianDocuments; }
    public DianSyncsApi dianSyncs() { return dianSyncs; }
    public InvoiceBatchesApi invoiceBatches() { return invoiceBatches; }
    public NumberingAssociationsApi numberingAssociations() { return numberingAssociations; }
    public NumberingRangesApi numberingRanges() { return numberingRanges; }
    public NumberingRequestsApi numberingRequests() { return numberingRequests; }
    public PaperNumberingRangesApi paperNumberingRanges() { return paperNumberingRanges; }
    public ProductsApi products() { return products; }
    public RadianStatusApi radianStatus() { return radianStatus; }
    public ReportRunsApi reportRuns() { return reportRuns; }
    public ReportsApi reports() { return reports; }

    /**
     * Recorre todas las páginas de un listado, perezosamente:
     * {@code for (Document f : trevol.paginate(c -> trevol.invoices().listInvoices().limit(100).startingAfter(c).execute(), InvoiceList::getData)) { … }}
     */
    public <T, P> Iterable<T> paginate(Paginacion.Pagina<P> pagina, Function<P, List<T>> datos) {
        return Paginacion.paginar(pagina, datos);
    }

    /** La llave no se guarda en el objeto; un toString nunca debe enseñar nada sensible. */
    @Override
    public String toString() {
        return "Trevol{environment=" + environment + ", keyType=" + keyType + (company != null ? ", company=" + company : "") + ", apiVersion=" + apiVersion + "}";
    }

    /** Opciones del cliente. */
    public static final class Builder {
        private String apiKey;
        private String apiVersion;
        private int maxRetries = 2;
        private Duration timeout = Duration.ofSeconds(65);
        private Duration maxRetryAfter = Duration.ofSeconds(60);
        private String baseUrl = BASE_URL;
        private Environment environment;
        private String company;
        private OkHttpClient httpClient;
        private DoubleConsumer dormir;
        private DoubleSupplier azar;
        private Supplier<String> uuid;
        private Supplier<Instant> ahora;

        private Builder() {}

        /** {@code tvl_test_…} o {@code tvl_live_…} (de una empresa), o {@code tva_test_…} o {@code tva_live_…} (de administración); si falta se lee {@code TREVOL_API_KEY}. */
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        /** El valor de {@code Trevol-Version} en cada petición (por defecto {@link #API_VERSION}). */
        public Builder apiVersion(String apiVersion) { this.apiVersion = apiVersion; return this; }
        /** Reintentos tras el primer intento (3 intentos en total por defecto). 0 los apaga. */
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        /** Tiempo máximo por intento (65 s por defecto, como el playground). Se ignora si pasas tu propio {@code OkHttpClient}. */
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }
        /** Si {@code Retry-After} pide esperar más que esto (60 s por defecto), no se espera: se lanza el error con {@code getRetryAfter()}. */
        public Builder maxRetryAfter(Duration maxRetryAfter) { this.maxRetryAfter = maxRetryAfter; return this; }
        /** Por defecto https://api.trevol.si/v1 (para un servidor simulado en las pruebas). */
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        /** Si se pasa y la llave no coincide, {@link ConfigurationException} ANTES de llamar. */
        public Builder environment(Environment environment) { this.environment = environment; return this; }
        /**
         * Solo con una llave de administración: la empresa ({@code com_…}) de las rutas de una empresa. Viaja en {@code Trevol-Company}, con
         * {@code Trevol-Environment} según la llave. Con una llave {@code tvl_} o un id mal formado, {@link ConfigurationException}.
         */
        public Builder company(String company) { this.company = company; return this; }
        /** Tu propio {@code OkHttpClient} (proxy, TLS, métricas): el SDK le agrega su interceptor y apaga {@code retryOnConnectionFailure}. */
        public Builder httpClient(OkHttpClient httpClient) { this.httpClient = httpClient; return this; }

        // Solo para las pruebas del paquete: el reloj, el azar y los UUID.
        Builder internos(DoubleConsumer dormir, DoubleSupplier azar, Supplier<String> uuid, Supplier<Instant> ahora) {
            this.dormir = dormir;
            this.azar = azar;
            this.uuid = uuid;
            this.ahora = ahora;
            return this;
        }

        public Trevol build() { return new Trevol(this); }
    }
}
