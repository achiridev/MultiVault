package dev.achiri.multivault.common.exception;

import java.util.UUID;

public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String recurso, Long id) {
        super(recurso + " con id " + id + " no encontrado");
    }
    public RecursoNoEncontradoException(String recurso, UUID id) {
        super(recurso + " con id " + id + " no encontrado");
    }
    public RecursoNoEncontradoException(String recurso, String nombre) {
        super(recurso + " '" + nombre + "' no encontrado");
    }
    public RecursoNoEncontradoException(String recurso) {
        super(recurso + " no encontrado");
    }
}
