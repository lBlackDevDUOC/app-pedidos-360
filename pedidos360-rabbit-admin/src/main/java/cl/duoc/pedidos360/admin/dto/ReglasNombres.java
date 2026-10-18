package cl.duoc.pedidos360.admin.dto;

/** Reglas de validación para nombres de recursos RabbitMQ, compartidas por DTOs y parámetros de ruta. */
public final class ReglasNombres {

    /**
     * Empieza con letra o número; luego letras, números, '.', '_', '-' o ':'; máximo 200 caracteres;
     * sin espacios y sin el prefijo "amq." (reservado por RabbitMQ).
     */
    public static final String PATRON_NOMBRE = "^(?!amq\\.)[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$";
    public static final String MENSAJE_NOMBRE =
            "debe empezar con letra o número, usar solo letras, números, '.', '_', '-' o ':' "
                    + "(máx. 200, sin espacios) y no puede usar el prefijo reservado 'amq.'";

    /** Routing key: palabras separadas por '.', admite los comodines '*' y '#'. Vacía es válida (fanout). */
    public static final String PATRON_ROUTING_KEY = "^[A-Za-z0-9._*#-]{0,255}$";
    public static final String MENSAJE_ROUTING_KEY =
            "solo admite letras, números, '.', '_', '-' y los comodines '*' y '#' (máx. 255)";

    private ReglasNombres() {
    }
}
