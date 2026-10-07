package pe.utec.planificador.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Extrae el texto de un sílabo en PDF con Apache PDFBox. */
@Service
public class LectorPdfService {

    /** Menos texto que esto suele indicar un PDF escaneado (imágenes). */
    private static final int MINIMO_CARACTERES = 200;
    /** Límite generoso para no enviar documentos enormes a la IA. */
    private static final int MAXIMO_CARACTERES = 300_000;

    public String extraerTexto(MultipartFile archivo) {
        validarArchivo(archivo);
        try {
            return extraerTexto(archivo.getBytes());
        } catch (IOException e) {
            throw new SilaboException("No se pudo leer el PDF. Verifica que el archivo no esté dañado.", e);
        }
    }

    public String extraerTexto(byte[] pdf) {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento).strip();
            if (texto.length() < MINIMO_CARACTERES) {
                throw new SilaboException("El PDF casi no tiene texto seleccionable (¿es un escaneo?). "
                        + "Prueba con la versión digital del sílabo.");
            }
            if (texto.length() > MAXIMO_CARACTERES) {
                throw new SilaboException("El PDF es demasiado largo para un sílabo (" + documento.getNumberOfPages()
                        + " páginas). Sube solo el sílabo del curso.");
            }
            return texto;
        } catch (InvalidPasswordException e) {
            throw new SilaboException("El PDF está protegido con contraseña.", e);
        } catch (IOException e) {
            throw new SilaboException("No se pudo leer el PDF. Verifica que el archivo no esté dañado.", e);
        }
    }

    private void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new SilaboException("Selecciona un archivo PDF.");
        }
        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename().toLowerCase();
        if (!nombre.endsWith(".pdf")) {
            throw new SilaboException("El archivo debe ser un PDF.");
        }
    }
}
