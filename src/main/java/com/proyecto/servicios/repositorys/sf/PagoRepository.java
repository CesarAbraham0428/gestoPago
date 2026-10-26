package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.Pago;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoRepository extends JpaRepository<Pago, Integer> {

    boolean existsByUpc(String upc);

    /**
     * Pagos sin resultado que todavía se pueden consultar con confirmTx (vigencia de 24 horas):
     * EN_REVISION con al menos 62 segundos de antigüedad, o PENDIENTE abandonados (p. ej. la API se reinició).
     */
    @Query("""
            select p.id from Pago p
            where p.fechaCreacion >= :vigencia
              and ((p.estado = com.proyecto.servicios.entity.sf.EstadoPago.EN_REVISION and p.fechaCreacion <= :limiteRevision)
                or (p.estado = com.proyecto.servicios.entity.sf.EstadoPago.PENDIENTE and p.fechaCreacion <= :limitePendiente))
            order by p.id
            """)
    List<Integer> idsPorConfirmar(@Param("vigencia") Instant vigencia,
            @Param("limiteRevision") Instant limiteRevision, @Param("limitePendiente") Instant limitePendiente);
}
