package in.nic.kvkekh.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GalleryServiceTest {

    @Test
    void descriptiveFileNamesBecomeCaptions() {
        assertThat(GalleryService.captionFromFileName("Field preparation for SRI")).isEqualTo("Field preparation for SRI");
        assertThat(GalleryService.captionFromFileName("potato_plant")).isEqualTo("Potato plant");
        assertThat(GalleryService.captionFromFileName("Awareness 1")).isEqualTo("Awareness 1");
    }

    @Test
    void cameraFileNamesDoNotBecomeCaptions() {
        assertThat(GalleryService.captionFromFileName("IMG_20191111_112947496_HDR")).isNull();
        assertThat(GalleryService.captionFromFileName("DSC00705")).isNull();
        assertThat(GalleryService.captionFromFileName("IMG-20190917-WA0010")).isNull();
        assertThat(GalleryService.captionFromFileName("WhatsApp Image 2020-06-05 at 3.23.12 PM")).isNull();
        assertThat(GalleryService.captionFromFileName("vlcsnap-2014-03-11-14h09m50s121")).isNull();
        assertThat(GalleryService.captionFromFileName("image12")).isNull();
        assertThat(GalleryService.captionFromFileName("QRTEAM")).isNull();
    }
}
