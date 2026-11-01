package si.trevol;

import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import si.trevol.generado.ApiCallback;
import si.trevol.generado.ApiClient;
import si.trevol.generado.ApiException;
import si.trevol.generado.Pair;

/**
 * El {@code ApiClient} generado con una sola vista previa: valida la Idempotency-Key de la persona ANTES de que OkHttp arme la petición
 * (OkHttp rechaza con {@code IllegalArgumentException} un valor de cabecera con caracteres que no son ASCII, y el error debe ser el del SDK).
 */
final class ClienteApi extends ApiClient {
    ClienteApi(OkHttpClient http) {
        super(http);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Request buildRequest(String baseUrl, String path, String method, List<Pair> queryParams, List<Pair> collectionQueryParams, Object body,
                                Map<String, String> headerParams, Map<String, String> cookieParams, Map<String, Object> formParams,
                                String[] authNames, ApiCallback callback) throws ApiException {
        String clave = headerParams.get("Idempotency-Key");
        if (clave != null) Transporte.validarClave(clave);
        return super.buildRequest(baseUrl, path, method, queryParams, collectionQueryParams, body, headerParams, cookieParams, formParams, authNames, callback);
    }
}
