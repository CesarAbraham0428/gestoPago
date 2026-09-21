package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.entity.gestopago.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    List<Producto> findAllByOrderByIdAsc();

    @Query(value = "WITH catalog_lock AS (SELECT pg_advisory_xact_lock(7412609, 1)) SELECT 1 FROM catalog_lock",
            nativeQuery = true)
    Integer bloquearEscriturasCatalogo();
}
