package pe.utec.planificador.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LectorPdfServiceTest {

    private final LectorPdfService lector = new LectorPdfService();

    @Test
    void extraeElTextoDeUnPdf() throws IOException {
        String linea = "MT5307 Control Inteligente - Sistema de evaluacion: EC1 semana 6 peso 19.8 por ciento";
        MockMultipartFile archivo = new MockMultipartFile("archivo", "silabo.pdf", "application/pdf", crearPdf(linea, 5));

        assertThat(lector.extraerTexto(archivo)).contains("MT5307 Control Inteligente").contains("EC1 semana 6");
    }

    @Test
    void rechazaArchivosQueNoSonPdf() {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "silabo.docx", "application/msword", new byte[]{1, 2});
        assertThatThrownBy(() -> lector.extraerTexto(archivo)).isInstanceOf(SilaboException.class).hasMessageContaining("PDF");
    }

    @Test
    void avisaSiElPdfNoTieneTexto() throws IOException {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "escaneado.pdf", "application/pdf", crearPdf("", 0));
        assertThatThrownBy(() -> lector.extraerTexto(archivo)).isInstanceOf(SilaboException.class).hasMessageContaining("escaneo");
    }

    @Test
    void avisaSiElArchivoEstaDanado() {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "roto.pdf", "application/pdf", "no es un pdf".getBytes());
        assertThatThrownBy(() -> lector.extraerTexto(archivo)).isInstanceOf(SilaboException.class).hasMessageContaining("dañado");
    }

    private byte[] crearPdf(String linea, int repeticiones) throws IOException {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                for (int i = 0; i < repeticiones; i++) {
                    contenido.beginText();
                    contenido.newLineAtOffset(40, 700 - i * 20);
                    contenido.showText(linea);
                    contenido.endText();
                }
            }
            documento.save(salida);
            return salida.toByteArray();
        }
    }
}
