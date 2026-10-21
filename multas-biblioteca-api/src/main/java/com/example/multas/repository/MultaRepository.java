package com.example.multas.repository;

import com.example.multas.model.EstadoMulta;
import com.example.multas.model.Multa;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MultaRepository extends JpaRepository<Multa, Long> {

    List<Multa> findByEstudianteId(String estudianteId);

    long countByEstudianteIdAndEstado(String estudianteId, EstadoMulta estado);
}
