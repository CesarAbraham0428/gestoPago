package com.proyecto.servicios.service.exception;



public class CorreoDuplicadoException extends ClienteYaRegistradoException {
    public CorreoDuplicadoException() { super( "Correo electrónico duplicado"); }
}
