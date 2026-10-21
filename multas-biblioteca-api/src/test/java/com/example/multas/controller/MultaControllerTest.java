package com.example.multas.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MultaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private void crearMulta(String estudiante, int dias) throws Exception {
        mockMvc.perform(post("/api/multas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estudianteId\":\"" + estudiante + "\",\"concepto\":\"Libro\",\"diasAtraso\":" + dias + "}"))
            .andExpect(status().isCreated());
    }

    @Test
    void listar_alInicio_devuelveListaVacia() throws Exception {
        mockMvc.perform(get("/api/multas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void generar_conDatosValidos_calculaMonto() throws Exception {
        mockMvc.perform(post("/api/multas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estudianteId\":\"1152001\",\"concepto\":\"Calculo I\",\"diasAtraso\":4}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.monto").value(2000))
            .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void generar_sinEstudianteId_devuelve400() throws Exception {
        mockMvc.perform(post("/api/multas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"concepto\":\"Calculo I\",\"diasAtraso\":4}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.estudianteId").exists());
    }

    @Test
    void generar_cuartaMultaPendiente_devuelve409() throws Exception {
        crearMulta("1152001", 1);
        crearMulta("1152001", 2);
        crearMulta("1152001", 3);

        mockMvc.perform(post("/api/multas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estudianteId\":\"1152001\",\"concepto\":\"Libro\",\"diasAtraso\":1}"))
            .andExpect(status().isConflict());
    }

    @Test
    void buscar_conIdInexistente_devuelve404() throws Exception {
        mockMvc.perform(get("/api/multas/999")).andExpect(status().isNotFound());
    }

    @Test
    void pagarEnVentanilla_marcaPagadaYRepetirDevuelve409() throws Exception {
        crearMulta("1152001", 2);

        mockMvc.perform(patch("/api/multas/1/pagar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PAGADA"))
            .andExpect(jsonPath("$.metodoPago").value("VENTANILLA"));
        mockMvc.perform(patch("/api/multas/1/pagar")).andExpect(status().isConflict());
    }
}
