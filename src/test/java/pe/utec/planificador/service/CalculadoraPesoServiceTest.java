package pe.utec.planificador.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraPesoServiceTest {

    private final CalculadoraPesoService calculadora = new CalculadoraPesoService();

    @Test
    void ec1EsSesentaPorTreintaYTres() {
        assertThat(calculadora.pesoReal(60, 33)).isEqualTo(19.8);
    }

    @Test
    void ec3EsSesentaPorTreintaYCuatro() {
        assertThat(calculadora.pesoReal(60, 34)).isEqualTo(20.4);
    }

    @Test
    void laboratorioEsVeintePorCatorce() {
        assertThat(calculadora.pesoReal(20, 14)).isEqualTo(2.8);
    }
}
