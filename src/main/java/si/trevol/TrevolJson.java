package si.trevol;

import com.google.gson.Gson;
import si.trevol.generado.JSON;

/**
 * Ayudantes de JSON con la misma configuración de Gson que usa el código generado (fechas, campos desconocidos…). Sirven para armar un
 * cuerpo de petición desde JSON ({@code TrevolJson.fromJson(texto, InvoiceInput.class)}; los modelos generados también tienen
 * {@code InvoiceInput.fromJson(texto)}) o para imprimir una respuesta.
 */
public final class TrevolJson {
    private TrevolJson() {}

    private static final Gson GSON = JSON.getGson();

    public static <T> T fromJson(String json, Class<T> clase) {
        return GSON.fromJson(json, clase);
    }

    public static String toJson(Object modelo) {
        return GSON.toJson(modelo);
    }
}
