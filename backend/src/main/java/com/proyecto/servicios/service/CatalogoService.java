package com.proyecto.servicios.service;

import com.proyecto.servicios.models.response.CatalogosResponse;
import jakarta.persistence.EntityManager;
import java.text.Collator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogos de países, nacionalidades y ocupaciones (tablas {@code paises}, {@code nacionalidades}, {@code ocupaciones}). */
@Service
@Transactional(readOnly = true)
public class CatalogoService {

    private static final String PAISES = "paises";
    private static final String NACIONALIDADES = "nacionalidades";
    private static final String OCUPACIONES = "ocupaciones";

    private final EntityManager em;

    public CatalogoService(EntityManager em) {
        this.em = em;
    }

    public CatalogosResponse obtener() {
        return new CatalogosResponse(nombres(PAISES), nombres(NACIONALIDADES), nombres(OCUPACIONES));
    }

    public boolean existePais(String nombre) {
        return existe(PAISES, nombre);
    }

    public boolean existeNacionalidad(String nombre) {
        return existe(NACIONALIDADES, nombre);
    }

    public boolean existeOcupacion(String nombre) {
        return existe(OCUPACIONES, nombre);
    }

    // Las tablas son constantes de esta clase; el valor buscado siempre viaja como parámetro.
    @SuppressWarnings("unchecked")
    private List<String> nombres(String tabla) {
        List<String> nombres = em.createNativeQuery("select nombre from " + tabla).getResultList();
        // En español, "Bélgica" va junto a "Belice" y no después de la Z como en un orden por bytes.
        Collator orden = Collator.getInstance(Locale.forLanguageTag("es-MX"));
        return nombres.stream().sorted(orden).toList();
    }

    private boolean existe(String tabla, String nombre) {
        return !em.createNativeQuery("select 1 from " + tabla + " where nombre = :nombre")
                .setParameter("nombre", nombre).setMaxResults(1).getResultList().isEmpty();
    }
}
