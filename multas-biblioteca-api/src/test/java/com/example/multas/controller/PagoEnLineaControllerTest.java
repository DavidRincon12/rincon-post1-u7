package com.example.multas.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.domain.port.PasarelaPagoPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PagoEnLineaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PasarelaPagoPort pasarela;

    @BeforeEach
    void crearMulta() throws Exception {
        mockMvc.perform(post("/api/multas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estudianteId\":\"1152001\",\"concepto\":\"Libro\",\"diasAtraso\":2}"))
            .andExpect(status().isCreated());
    }

    @Test
    void pagarEnLinea_pagoAprobado_marcaPagadaConElProveedor() throws Exception {
        when(pasarela.procesar(any())).thenReturn(new ResultadoPago("PAGOSUDES", true, "TX-1", "ok"));

        mockMvc.perform(post("/api/multas/1/pagar-en-linea"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PAGADA"))
            .andExpect(jsonPath("$.metodoPago").value("PAGOSUDES"));
    }

    @Test
    void pagarEnLinea_pagoRechazado_devuelve402() throws Exception {
        when(pasarela.procesar(any())).thenReturn(new ResultadoPago("WOMPI", false, null, "Pago rechazado por Wompi"));

        mockMvc.perform(post("/api/multas/1/pagar-en-linea"))
            .andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.error").value("Pago rechazado por Wompi"));
    }

    @Test
    void pagarEnLinea_multaYaPagadaEnVentanilla_devuelve409() throws Exception {
        mockMvc.perform(patch("/api/multas/1/pagar")).andExpect(status().isOk());

        mockMvc.perform(post("/api/multas/1/pagar-en-linea")).andExpect(status().isConflict());
    }
}
