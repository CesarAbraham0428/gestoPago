package com.proyecto.servicios.models.response;

import java.util.List;

/** Opciones de las listas desplegables del registro y del perfil, ordenadas alfabéticamente. */
public record CatalogosResponse(List<String> paises, List<String> nacionalidades, List<String> ocupaciones) { }
