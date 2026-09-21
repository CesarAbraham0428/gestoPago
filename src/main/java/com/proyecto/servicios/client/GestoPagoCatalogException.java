package com.proyecto.servicios.client;

public class GestoPagoCatalogException extends RuntimeException {

    public GestoPagoCatalogException(String message) {
        super(message);
    }

    public GestoPagoCatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}