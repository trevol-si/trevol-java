package si.trevol;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import si.trevol.generado.ApiException;

/** Paginación con iteradores (docs/api/43 §2.6.7): la API pagina por cursor ({@code starting_after}). */
public final class Paginacion {
    private Paginacion() {}

    /** Pide UNA página: {@code starting_after = cursor} (null la primera). Puede lanzar el {@code ApiException} del código generado. */
    @FunctionalInterface
    public interface Pagina<P> {
        P pedir(String cursor) throws ApiException;
    }

    /**
     * Recorre todas las páginas de un listado, perezosamente.
     *
     * <p>{@code pagina.pedir(cursor)} pide UNA página ({@code starting_after = cursor}; null la primera) y devuelve el sobre de lista generado
     * ({@code InvoiceList}, {@code EventList}…); {@code datos} saca de él los elementos. Sigue con {@code next_cursor} mientras {@code has_more};
     * si la página no lo trae, usa el {@code id} del último elemento (así lo arma la API). Se detiene si el cursor no avanza: una API que
     * repitiera el mismo cursor no puede dejar el bucle girando para siempre.
     *
     * <pre>{@code
     * for (Document f : trevol.paginate(c -> trevol.invoices().listInvoices().limit(100).startingAfter(c).execute(), InvoiceList::getData)) { ... }
     * }</pre>
     */
    public static <T, P> Iterable<T> paginar(Pagina<P> pagina, Function<P, List<T>> datos) {
        return () -> new Iterator<T>() {
            private String cursor = null;
            private Iterator<T> actual = Collections.emptyIterator();
            private boolean hayMas = true;
            private boolean pedida = false;

            private void llenar() {
                while (!actual.hasNext() && hayMas) {
                    P p;
                    try {
                        p = pagina.pedir(cursor);
                    } catch (ApiException e) { // el cliente generado solo lanza esto si no pudo leer la respuesta: los errores de la API son TrevolException
                        throw new TrevolException("El cliente generado no pudo completar la llamada: " + e.getMessage(), e.getCode() == 0 ? null : e.getCode(),
                                null, null, null, null, null, null, null, null, null, null, null, e);
                    }
                    List<T> lista = datos.apply(p);
                    if (lista == null) lista = Collections.emptyList();
                    actual = lista.iterator();
                    pedida = true;
                    boolean otra = Boolean.TRUE.equals(invocar(p, "getHasMore")) && !lista.isEmpty();
                    String siguiente = null;
                    if (otra) {
                        Object c = invocar(p, "getNextCursor");
                        siguiente = c instanceof String && !((String) c).isEmpty() ? (String) c : null;
                        if (siguiente == null) {
                            Object id = invocar(lista.get(lista.size() - 1), "getId");
                            siguiente = id instanceof String && !((String) id).isEmpty() ? (String) id : null;
                        }
                        if (siguiente == null || siguiente.equals(cursor)) otra = false;
                    }
                    hayMas = otra;
                    cursor = siguiente;
                }
            }

            @Override
            public boolean hasNext() {
                llenar();
                return actual.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                return actual.next();
            }
        };
    }

    private static Object invocar(Object objeto, String metodo) {
        try {
            Method m = objeto.getClass().getMethod(metodo);
            return m.invoke(objeto);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
