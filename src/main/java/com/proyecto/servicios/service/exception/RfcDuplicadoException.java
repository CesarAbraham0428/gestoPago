package com.proyecto.servicios.service.exception;



public class RfcDuplicadoException extends ClienteYaRegistradoException {
    public RfcDuplicadoException() { super( "RFC duplicado"); }
}
