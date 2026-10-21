package com.example.multas.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.infrastructure.pago.PagosUdesAdapter;
import com.example.multas.infrastructure.pago.WompiAdapter;
import com.example.multas.model.Multa;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AdaptadoresPagoTest {

    private static final String URL_UDES = "http://pasarela/pagosudes";
    private static final String URL_WOMPI = "http://pasarela/wompi";

    private RestTemplate restTemplate;
    private MockRestServiceServer servidor;
    private Multa multa;

    @BeforeEach
    void preparar() {
        restTemplate = new RestTemplate();
        servidor = MockRestServiceServer.createServer(restTemplate);
        multa = new Multa();
        multa.setEstudianteId("1152001");
        multa.setMonto(new BigDecimal("2000"));
    }

    @Test
    void pagosUdes_respuestaAprobada_devuelveResultadoExitoso() {
        PagosUdesAdapter adaptador = new PagosUdesAdapter(restTemplate);
        ReflectionTestUtils.setField(adaptador, "urlPasarela", URL_UDES);
        servidor.expect(requestTo(URL_UDES))
            .andRespond(withSuccess("{\"idTransaccion\":\"TX-1\",\"estadoTransaccion\":\"APROBADA\"}",
                MediaType.APPLICATION_JSON));

        ResultadoPago resultado = adaptador.procesar(multa);

        assertThat(resultado.exitoso()).isTrue();
        assertThat(resultado.proveedor()).isEqualTo("PAGOSUDES");
        assertThat(resultado.referenciaExterna()).isEqualTo("TX-1");
    }

    @Test
    void pagosUdes_pasarelaCaida_devuelveResultadoFallido() {
        PagosUdesAdapter adaptador = new PagosUdesAdapter(restTemplate);
        ReflectionTestUtils.setField(adaptador, "urlPasarela", URL_UDES);
        servidor.expect(requestTo(URL_UDES)).andRespond(withServerError());

        ResultadoPago resultado = adaptador.procesar(multa);

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.mensaje()).contains("no disponible");
    }

    @Test
    void wompi_enviaMontoEnCentavosYTraduceLaRespuesta() {
        WompiAdapter adaptador = new WompiAdapter(restTemplate);
        ReflectionTestUtils.setField(adaptador, "urlPasarela", URL_WOMPI);
        servidor.expect(requestTo(URL_WOMPI))
            .andExpect(jsonPath("$.amountInCents").value(200000))
            .andRespond(withSuccess("{\"reference\":\"W-9\",\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));

        ResultadoPago resultado = adaptador.procesar(multa);

        assertThat(resultado.exitoso()).isTrue();
        assertThat(resultado.proveedor()).isEqualTo("WOMPI");
        assertThat(resultado.referenciaExterna()).isEqualTo("W-9");
    }

    @Test
    void wompi_respuestaRechazada_devuelveResultadoFallido() {
        WompiAdapter adaptador = new WompiAdapter(restTemplate);
        ReflectionTestUtils.setField(adaptador, "urlPasarela", URL_WOMPI);
        servidor.expect(requestTo(URL_WOMPI))
            .andRespond(withSuccess("{\"reference\":\"W-9\",\"status\":\"DECLINED\"}", MediaType.APPLICATION_JSON));

        assertThat(adaptador.procesar(multa).exitoso()).isFalse();
    }
}
