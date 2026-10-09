package com.proyecto.servicios.service.exception;



public class CurpDuplicadaException extends ClienteYaRegistradoException {
    public CurpDuplicadaException() { super( "CURP duplicada"); }
}
