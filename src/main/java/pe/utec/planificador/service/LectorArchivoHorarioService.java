package pe.utec.planificador.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pe.utec.planificador.dto.ArchivoParaIa;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/** Valida la foto del horario o el consolidado de matrícula antes de enviarlo a la IA. */
@Service
public class LectorArchivoHorarioService {

    /** Formatos que aceptan tanto Claude como Gemini. */
    private static final Map<String, String> TIPOS_POR_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "pdf", "application/pdf");

    private static final long TAMANO_MAXIMO = 15L * 1024 * 1024;

    public ArchivoParaIa leer(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new SilaboException("Selecciona una foto de tu horario o el PDF de tu matrícula.");
        }
        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename();
        String extension = nombre.contains(".") ? nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (extension.equals("heic") || extension.equals("heif")) {
            throw new SilaboException("Las fotos HEIC del iPhone no son compatibles. Toma una captura de pantalla "
                    + "o exporta la foto como JPG.");
        }
        String tipo = TIPOS_POR_EXTENSION.get(extension);
        if (tipo == null) {
            throw new SilaboException("Formato no compatible. Sube una imagen JPG, PNG o WEBP, o un PDF.");
        }
        if (archivo.getSize() > TAMANO_MAXIMO) {
            throw new SilaboException("El archivo pesa más de 15 MB. Prueba con una captura de pantalla.");
        }
        try {
            return new ArchivoParaIa(archivo.getBytes(), tipo, nombre);
        } catch (IOException e) {
            throw new SilaboException("No se pudo leer el archivo. Inténtalo de nuevo.", e);
        }
    }
}
