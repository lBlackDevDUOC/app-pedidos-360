package cl.duoc.pedidos360.kafkaadmin.service;

/** Errores de negocio de la capa de administración. El ErroresApiHandler los traduce a 400/404/409/503. */
public final class AdminExceptions {

    private AdminExceptions() {}

    /** 404 */
    public static class RecursoNoEncontrado extends RuntimeException {
        public RecursoNoEncontrado(String mensaje) { super(mensaje); }
    }

    /** 409: el tópico o grupo ya existe. */
    public static class RecursoYaExiste extends RuntimeException {
        public RecursoYaExiste(String mensaje) { super(mensaje); }
    }

    /** 409: tópico del sistema (no se elimina) o grupo con consumidores activos. */
    public static class OperacionNoPermitida extends RuntimeException {
        public OperacionNoPermitida(String mensaje) { super(mensaje); }
    }

    /** 400: combinación de parámetros que Kafka rechazaría (o que no queremos permitir). */
    public static class ConfiguracionInvalida extends RuntimeException {
        public ConfiguracionInvalida(String mensaje) { super(mensaje); }
    }

    /** 503: el clúster no respondió a tiempo. */
    public static class ClusterNoDisponible extends RuntimeException {
        public ClusterNoDisponible(String mensaje, Throwable causa) { super(mensaje, causa); }
    }
}
