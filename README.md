# trevol-java

SDK de Java para la **API de Trevol**: facturación electrónica, documento soporte, documento equivalente, nómina electrónica y eventos
RADIAN ante la DIAN (Colombia). Documentación: <https://platform.trevol.si/docs>.

> **In English.** Java SDK for the Trevol API (Colombian e-invoicing, DIAN). Java ≥ 11, OkHttp 4 + Gson. Typed models and API clients generated
> from the OpenAPI spec (OpenAPI Generator `okhttp-gson`, package `si.trevol.generado`), plus a thin hand-written layer (package `si.trevol`):
> automatic `Idempotency-Key` (the same one on retries), retries with exponential backoff honouring `Retry-After`, typed unchecked exceptions
> by problem+json `code`, environment inferred from the key prefix (`tvl_test_` = DIAN test environment, `tvl_live_` = production), pinned
> `Trevol-Version`, Standard Webhooks signature verification, cursor pagination with `Iterable` and PDF/XML/ZIP downloads. Docs are in Spanish
> like the API.

**Versión 0.1.0** — API `2026-10-22`. Licencia MIT.

## Instalación

```xml
<dependency>
  <groupId>si.trevol</groupId>
  <artifactId>trevol-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

```kotlin
implementation("si.trevol:trevol-java:0.1.0")   // Gradle
```

Necesita Java 11 o más nuevo. Depende de OkHttp 4, Gson y de la biblioteca abierta
[`com.standardwebhooks:standardwebhooks`](https://central.sonatype.com/artifact/com.standardwebhooks/standardwebhooks) (MIT, sin dependencias).

## Uso

La llave de la API se crea en la consola de desarrolladores (<https://platform.trevol.si/llaves>) y se guarda en el entorno de tu **servidor**:
`TREVOL_API_KEY`. Una llave `tvl_test_…` llama a la habilitación de la DIAN; una `tvl_live_…`, a producción. El SDK deduce el entorno del
prefijo (`trevol.environment()`).

```java
import si.trevol.*;
import si.trevol.generado.model.*;

Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).build(); // tvl_test_… → trevol.environment() == TEST

try {
    InvoiceInput entrada = InvoiceInput.fromJson("""
        {"external_id": "VENTA-2026-000123", "customer": {"final_consumer": true},
         "items": [{"description": "Café especial 500 g", "quantity": "1", "unit_price": "32000", "taxes": [{"code": "01", "rate": "19"}]}],
         "payment": {"form": "cash", "methods": [{"code": "10"}]}}
        """);
    Document factura = trevol.invoices().createInvoice(entrada)
        .idempotencyKey("VENTA-2026-000123") // opcional: si falta, el SDK pone una y la repite en los reintentos
        .execute();
    if ("processing".equals(factura.getStatus())) {
        // 202: la DIAN no contestó; Trevol lo termina y avisa por webhook. NO crees otro documento para la misma venta.
    }
} catch (ValidationException e) {
    e.getErrors().forEach(x -> System.err.println(x.getPointer() + " " + x.getMessage()));
} catch (TrevolException e) {
    System.err.println(e.getCode() + " " + e.getRequestId());
}

// Todas las páginas de un listado, perezosamente
for (Document f : trevol.paginate(c -> trevol.invoices().listInvoices().limit(100).startingAfter(c).execute(), InvoiceList::getData)) {
    System.out.println(f.getId() + " " + f.getStatus());
}

// Los archivos llegan como java.io.File temporal
File pdf = trevol.invoices().downloadInvoicePdf("inv_…").execute();
```

Los nombres de los métodos son los del `operationId` de la [especificación](https://platform.trevol.si/docs/api) (`createInvoice`, `listInvoices`,
`retrieveInvoice`, `correctInvoice`, `sendInvoice`, `downloadInvoicePdf`…). Cada petición se arma **por pasos**: lo obligatorio va en el método
y lo opcional en un método por parámetro (`.limit(100)`, `.startingAfter(c)`, `.idempotencyKey("…")`), así que añadir un filtro a la API nunca
rompe tu compilación. El cuerpo se arma con el modelo generado o, lo más cómodo, desde JSON (`InvoiceInput.fromJson(texto)`;
`TrevolJson.fromJson(texto, Clase.class)` hace lo mismo con la configuración de Gson del SDK); los importes y las fechas viajan como texto
(`"32000.50"`, `"2026-10-31"`). (Los bloques de texto `"""` necesitan Java 15+ en *tu* código; el SDK funciona desde Java 11.)

## Lo que conviene saber

### Idempotencia: manda `external_id` y tu propia clave

El SDK genera una `Idempotency-Key` por llamada y **la repite en todos los reintentos de esa llamada**. Pero si tu proceso muere y vuelves a
llamar, saldrá otra clave y, si no mandaste `external_id` (es opcional), podría duplicarse el documento. Por eso:

- manda siempre `external_id` (tu id de la venta), y
- pasa una `idempotencyKey` derivada de tu venta (`.idempotencyKey("VENTA-2026-000123")`).

Ante un `ConflictException` con `getCode()` igual a `external_id_in_use`, el documento ya existe: usa `e.getProblem().get("existing_id")`. Toda
excepción lleva la clave que se usó (`getIdempotencyKey()`) para reintentar más tarde con la misma. Una `ApiConnectionException` también.

### Respuestas 202 (`processing`, `sending`, `validating`, `contingency`)

No son errores ni se reintentan: se devuelven como el modelo de la respuesta. **No crees otro documento**: espera el webhook o consulta con
`retrieveInvoice`; repetir la misma llamada con la misma `Idempotency-Key` continúa el mismo documento. El PDF y el ZIP llegan después del `201`
(evento `invoice.delivered`, o `delivery.status` igual a `completed`).

### Reintentos

Hasta 2 reintentos (3 intentos) con espera creciente (0,5 s, 1 s, 2 s… con tope de 8 s y un poco de azar) ante un error de red, 429, 500, 502,
503, 504, un 409 `request_in_progress` o `previous_event_pending`, y todo problema que diga `retryable: true` (salvo el 402). Respeta
`Retry-After`; si pide más de `maxRetryAfter` (60 s) no espera y lanza la excepción con `getRetryAfter()`. Las operaciones sin
`Idempotency-Key` (crear un endpoint, `…/send`, `DELETE`) solo se reintentan con 429. Los 202 y los errores finales (400, 401, 402, 403, 404,
422 y los 409 `invalid_state`, `external_id_in_use`, `duplicate_document`) **nunca** se reintentan. El SDK apaga
`retryOnConnectionFailure` de OkHttp: es el único que reintenta. La espera bloquea el hilo.

### Solo métodos síncronos

Usa `execute()`. Las excepciones del SDK son del interceptor de OkHttp; en una llamada asíncrona (`executeAsync(callback)`) OkHttp cancela la
llamada y entrega un `ApiException` que envuelve un `IOException` («canceled due to …»), no la `TrevolException`. Si necesitas concurrencia,
llama a `execute()` desde tus propios hilos o un `ExecutorService`.

### La versión de la API

El SDK fija `Trevol-Version: 2026-10-22` en cada petición. **Ojo:** una llave creada con la versión `2026-10-21` que empiece a usar este SDK
pasa a `2026-10-22` (el encabezado manda): recibe `202 processing` donde antes recibía `503`. Si todavía no quieres ese cambio:
`Trevol.builder().apiKey(llave).apiVersion("2026-10-21").build()`. Más en <https://platform.trevol.si/docs/versiones>.

### Excepciones tipadas

`TrevolException` (una `RuntimeException`) es la base; las clases salen del `code` del problem+json: `AuthenticationException`,
`PermissionDeniedException`, `NotFoundException`, `InvalidRequestException`, `IdempotencyException`, `ValidationException` (con `getErrors()`:
`getField()`, `getPointer()`, `getMessage()`), `ConflictException`, `PreconditionException`, `DianRejectedException` (con
`getProblem().get("document")` y `get("rules")`), `BalanceException`, `RateLimitException`, `DianUnavailableException` e
`InternalServerException`. Un `code` que el SDK todavía no conoce llega como `TrevolException` con ese `getCode()`: nunca rompe. Además:
`ApiConnectionException` (no hubo respuesta), `ConfigurationException` (mal uso, antes de llamar) y `WebhookVerificationException`
(`getReason()`: firma, fecha, cabeceras o cuerpo). Cada excepción tiene `getStatus()`, `getCode()`, `getType()`, `getTitle()`, `getDetail()`,
`getRequestId()`, `getRetryable()`, `getRetryAfter()`, `getErrors()`, `getIdempotencyKey()`, `getProblem()` (el cuerpo completo) y
`getHeaders()` (los de la respuesta). Ninguna incluye tu llave.

### Webhooks

```java
Webhook wh = new Webhook(System.getenv("TREVOL_WEBHOOK_SECRET")); // whsec_…

@PostMapping("/webhooks/trevol") // Spring MVC
ResponseEntity<Void> recibir(@RequestBody byte[] cuerpo, @RequestHeader Map<String, String> cabeceras) {
    try {
        Event evento = wh.verify(cuerpo, cabeceras); // el cuerpo CRUDO (byte[] o String), no un objeto ya parseado
        // Deduplica por evento.getId() (también llega en la cabecera webhook-id): la verificación no evita repeticiones.
        Object datos = wh.eventData(evento); // el modelo según el type (p. ej. DocumentEventData); null si el tipo es nuevo
        return ResponseEntity.ok().build();
    } catch (WebhookVerificationException e) {
        return ResponseEntity.badRequest().build();
    }
}
```

`@RequestHeader Map<String, String>` sirve igual en Spring Framework 6 y 7 (desde la 7, `HttpHeaders` ya no es un `Map`). En un Servlet: `request.getInputStream().readAllBytes()` y un `Map<String, List<String>>` con las cabeceras. La firma se calcula sobre los
**bytes exactos**. `verify` acepta `Map<String, List<String>>` o `Map<String, String>` en cualquier caja; la firma esperada la calcula la
biblioteca de Standard Webhooks, y el SDK tolera 5 minutos hacia atrás y hacia adelante en `webhook-timestamp` y compara en tiempo constante.

### Estado y cabeceras de la respuesta

Cada operación tiene `executeWithHttpInfo()` que devuelve `ApiResponse` con `getStatusCode()`, `getHeaders()` y `getData()`.

## Opciones

```java
Trevol.builder()
    .apiKey(llave)
    .apiVersion("2026-10-22")                    // Trevol-Version en cada petición
    .maxRetries(2)                               // 0 los apaga
    .timeout(Duration.ofSeconds(65))             // por intento
    .maxRetryAfter(Duration.ofSeconds(60))
    .baseUrl("https://api.trevol.si/v1")
    .environment(Trevol.Environment.TEST)        // si la llave no coincide: ConfigurationException antes de llamar
    .httpClient(miOkHttpClient)                  // proxy, TLS, métricas: el SDK le agrega su interceptor
    .build();
```

## Cómo está hecho

- `src/main/java/si/trevol/generado/` lo escribe [OpenAPI Generator](https://openapi-generator.tech) 7.26.0 (`java`, `okhttp-gson`) desde la
  especificación pública y **no se edita** (ver `GENERADO.md`). Para que un valor nuevo de un `enum` en lo que la API devuelve no rompa a quien
  ya integró, los modelos de respuesta se generan sin `enum`; y las listas sin escribir no se serializan como `[]` (`containerDefaultToNull`).
- Lo propio (`src/main/java/si/trevol/`: `Trevol`, `Transporte`, `Errores`, `Paginacion`, `Webhook`, `*Exception`) vive fuera y solo usa el punto
  de extensión del código generado: un `Interceptor` de OkHttp que lanza la excepción tipada y reintenta.
- Las pruebas: `mvn test` (unitarias sin red, contra un servidor HTTP local de MockWebServer, con los mismos casos de los demás SDK en
  `pruebas/datos/`); `TREVOL_PRISM_URL=http://127.0.0.1:4010 mvn test` suma las de un servidor simulado
  (`npx @stoplight/prism-cli mock pruebas/datos/openapi.json -p 4010`) que valida cada petición contra la especificación, y que además corre
  como programa cada fragmento de código de la documentación (se compilan siempre con `mvn test-compile`).
- `pruebas/humo/Humo.java` es la prueba de humo **real** (solo con una llave `tvl_test_`; no emite documentos).

## Licencia

MIT © 2026 Joshua Montclair (Trevol).
