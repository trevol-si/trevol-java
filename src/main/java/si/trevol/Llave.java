package si.trevol;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** La llave de la API: su formato y el entorno que se deduce de su prefijo (docs/api/43 §2.6.1). */
final class Llave {
    private Llave() {}

    private static final Pattern FORMATO = Pattern.compile("^tvl_(test|live)_[0-9a-f]{64}$");

    /** Valida la llave y devuelve su entorno: {@code test} (habilitación de la DIAN) o {@code live} (producción). El mensaje NUNCA incluye la llave. */
    static Trevol.Environment entorno(String llave) {
        Matcher m = llave == null ? null : FORMATO.matcher(llave.trim());
        if (m == null || !m.matches()) {
            throw new ConfigurationException("La llave de la API no tiene el formato esperado (tvl_test_… o tvl_live_… con 64 caracteres hexadecimales). "
                    + "Créala en https://platform.trevol.si/llaves y pásala completa o define TREVOL_API_KEY.");
        }
        return "test".equals(m.group(1)) ? Trevol.Environment.TEST : Trevol.Environment.LIVE;
    }
}
