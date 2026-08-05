package dev.achiri.multivault.common.exception;

public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }

    public AccesoDenegadoException(String modulo, String accion) {
        super("Acceso denegado al módulo '" + modulo + "' para la acción '" + accion + "'");
    }
}
