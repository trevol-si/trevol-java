package si.trevol;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La llave: su formato, su tipo y el entorno que se deduce de su prefijo (docs/api/43 §2.6.1). Una {@code tvl_} es la llave de una empresa;
 * una {@code tva_}, la llave de administración de una cuenta. En las dos, {@code _test_} es la habilitación de la DIAN y {@code _live_},
 * producción.
 */
final class Llave {
    private Llave() {}

    private static final Pattern FORMATO = Pattern.compile("^(tvl|tva)_(test|live)_[0-9a-f]{64}$");
    private static final Pattern EMPRESA = Pattern.compile("^com_[0-9a-f]{32}$");

    /** Lo que se sabe de una llave sin llamar a la API. */
    static final class Datos {
        final Trevol.KeyType tipo;
        final Trevol.Environment entorno;

        private Datos(Trevol.KeyType tipo, Trevol.Environment entorno) {
            this.tipo = tipo;
            this.entorno = entorno;
        }
    }

    /** Valida la llave y devuelve su tipo y su entorno. El mensaje NUNCA incluye la llave. */
    static Datos leer(String llave) {
        Matcher m = llave == null ? null : FORMATO.matcher(llave.trim());
        if (m == null || !m.matches()) {
            throw new ConfigurationException("La llave de la API no tiene el formato esperado (tvl_test_…, tvl_live_…, tva_test_… o tva_live_… con 64 "
                    + "caracteres hexadecimales). La de una empresa se crea en https://platform.trevol.si/llaves y la de administración en "
                    + "https://platform.trevol.si/administracion; pásala completa o define TREVOL_API_KEY.");
        }
        return new Datos("tva".equals(m.group(1)) ? Trevol.KeyType.ACCOUNT : Trevol.KeyType.API,
                "test".equals(m.group(2)) ? Trevol.Environment.TEST : Trevol.Environment.LIVE);
    }

    /** El entorno de una llave válida. */
    static Trevol.Environment entorno(String llave) {
        return leer(llave).entorno;
    }

    /** Valida el id de una empresa ({@code com_} y 32 caracteres hexadecimales en minúscula) y lo devuelve sin espacios. */
    static String empresa(String id) {
        String limpio = id == null ? null : id.trim();
        if (limpio == null || !EMPRESA.matcher(limpio).matches()) {
            throw new ConfigurationException("company debe ser el id de una empresa de tu cuenta: com_ y 32 caracteres hexadecimales en minúscula "
                    + "(lo devuelve trevol.account().listAccountCompanies()).");
        }
        return limpio;
    }
}
