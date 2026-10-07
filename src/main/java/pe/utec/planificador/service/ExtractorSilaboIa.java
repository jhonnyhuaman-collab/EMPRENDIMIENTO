package pe.utec.planificador.service;

import pe.utec.planificador.dto.ArchivoParaIa;
import pe.utec.planificador.dto.ia.HorarioMatriculaDto;
import pe.utec.planificador.dto.ia.SilaboExtraidoDto;

import java.time.LocalDate;

/** Un proveedor de IA capaz de leer sílabos y horarios de clase y devolverlos estructurados. */
public interface ExtractorSilaboIa {

    /** Identificador usado en planificador.ia.proveedor, ej. "anthropic". */
    String getClave();

    /** Nombre para mostrar, ej. "Claude (Anthropic)". */
    String getNombre();

    String getModelo();

    /** true si su API key está definida en las variables de entorno. */
    boolean estaConfigurado();

    SilaboExtraidoDto extraer(String textoSilabo, LocalDate inicioCiclo);

    /** Lee una foto de horario o un consolidado de matrícula (imagen o PDF). */
    HorarioMatriculaDto extraerHorario(ArchivoParaIa archivo);
}
