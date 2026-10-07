package pe.utec.planificador.service;

import org.springframework.stereotype.Service;

/**
 * Calcula el peso real de una evaluación en la nota final.
 * Ej.: "3 EC 60% (EC1 33%)" → EC1 = 60% × 33% = 19.8%.
 */
@Service
public class CalculadoraPesoService {

    public double pesoReal(double pesoComponente, double pesoDentroDelComponente) {
        double peso = pesoComponente * pesoDentroDelComponente / 100.0;
        return Math.round(peso * 100.0) / 100.0;
    }
}
