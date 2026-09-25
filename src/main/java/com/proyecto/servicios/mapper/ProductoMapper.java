package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.gestopago.Producto;
import com.proyecto.servicios.model.ProductoResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoProduct;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductoMapper {

    ProductoResponse toResponse(GestoPagoProduct producto);

    ProductoResponse toResponse(Producto producto);

    List<ProductoResponse> toResponses(List<GestoPagoProduct> productos);

    @Mapping(target = "id", ignore = true)
    Producto toEntity(ProductoResponse producto);

    List<Producto> toEntities(List<ProductoResponse> productos);
}
