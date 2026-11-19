// Generado por sdks/herramientas/fragmentos.mjs: el fragmento de la documentación, apuntando a Prism. No editar.
package programas;

import si.trevol.Trevol;
import si.trevol.generado.model.AccountCompanyInput;

public class EjemploCreateAccountCompany {
    public static void main(String[] args) throws Exception {
        Trevol trevol = Trevol.builder().apiKey(System.getenv("TREVOL_API_KEY")).baseUrl(System.getenv("TREVOL_PRISM_URL")).maxRetries(0).build(); // tvl_test_… → trevol.environment() == TEST

        AccountCompanyInput entrada = AccountCompanyInput.fromJson("""
            {
              "nit": "901234567",
              "name": "FERRETERÍA EL ROBLE SAS",
              "email": "facturacion@elroble.example"
            }
            """);
        var resultado = trevol.account().createAccountCompany(entrada)
                // .idempotencyKey("VENTA-2026-000123") // opcional: si falta, el SDK pone una y la repite en los reintentos
                .execute();
        System.out.println(resultado);
    }
}
