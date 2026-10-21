package com.example.multas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MultaTest {

    @Test
    void calcularMonto_conPocosDias_multiplicaPorValorDiario() {
        assertThat(Multa.calcularMonto(3)).isEqualByComparingTo(new BigDecimal("1500"));
    }

    @Test
    void calcularMonto_superandoElTope_devuelveTopeMaximo() {
        assertThat(Multa.calcularMonto(100)).isEqualByComparingTo(new BigDecimal("15000"));
    }

    @Test
    void marcarComoPagada_dosVeces_lanzaExcepcion() {
        Multa multa = new Multa();
        multa.marcarComoPagada("VENTANILLA");

        assertThat(multa.getEstado()).isEqualTo(EstadoMulta.PAGADA);
        assertThatThrownBy(() -> multa.marcarComoPagada("VENTANILLA"))
            .isInstanceOf(MultaYaPagadaException.class);
    }
}
