package pe.utec.planificador.dto;

/** Archivo (imagen o PDF) que se envía tal cual a la IA. */
public record ArchivoParaIa(byte[] datos, String tipoMime, String nombre) {

    public boolean esPdf() {
        return "application/pdf".equals(tipoMime);
    }
}
