package si.trevol;

/** Mal uso del SDK, antes de llamar: llave mal formada, entorno que no coincide, empresa mal formada o que falta con una llave de administración. Nunca incluye la llave. */
public class ConfigurationException extends TrevolException {
    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(message, null, null);
    }
}
