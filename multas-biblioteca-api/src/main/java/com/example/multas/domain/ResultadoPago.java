package com.example.multas.domain;

// Tipo de dominio: ninguna pasarela concreta se expone hacia MultaService.
public record ResultadoPago(
    String proveedor,
    boolean exitoso,
    String referenciaExterna,
    String mensaje
) {}
