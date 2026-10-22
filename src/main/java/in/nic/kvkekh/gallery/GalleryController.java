package in.nic.kvkekh.gallery;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import in.nic.kvkekh.content.Navigation;
import in.nic.kvkekh.content.Navigation.Crumb;
import in.nic.kvkekh.content.Page;
import in.nic.kvkekh.content.PageModel;
import in.nic.kvkekh.content.PageRegistry;
import in.nic.kvkekh.gallery.GalleryService.AlbumView;
import in.nic.kvkekh.gallery.GalleryService.Photo;

/**
 * Photo gallery: album index, one page per album, and one page per photo (with previous/next links), so
 * every photo is reachable and described without relying on a JavaScript lightbox.
 */
@Controller
class GalleryController {

    private final GalleryService gallery;
    private final PageRegistry pages;
    private final PageModel pageModel;
    private final Navigation navigation;

    GalleryController(GalleryService gallery, PageRegistry pages, PageModel pageModel, Navigation navigation) {
        this.gallery = gallery;
        this.pages = pages;
        this.pageModel = pageModel;
        this.navigation = navigation;
    }

    @GetMapping("/gallery")
    String index(Model model) {
        pageModel.apply(model, pages.require("gallery"));
        model.addAttribute("albums", gallery.albums());
        return "pages/gallery/index";
    }

    @GetMapping("/gallery/{slug}")
    String album(@PathVariable String slug, Model model) {
        AlbumView album = findAlbum(slug);
        pageModel.apply(model, albumPage(album));
        model.addAttribute("album", album);
        return "pages/gallery/album";
    }

    @GetMapping("/gallery/{slug}/{photoId}")
    String photo(@PathVariable String slug, @PathVariable String photoId, Model model) {
        AlbumView album = findAlbum(slug);
        Photo photo = album.photo(photoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<Photo> photos = album.photos();
        int index = photo.number() - 1;
        String title = album.album().title() + ": photo " + photo.number() + " of " + photos.size();

        Page albumPage = albumPage(album);
        Page photoPage = new Page("photo", album.path() + "/" + photo.id(), title, photo.caption(), null,
                "gallery", album.lastUpdated(), List.of());
        List<Crumb> crumbs = new ArrayList<>(navigation.breadcrumbs(albumPage));
        crumbs.set(crumbs.size() - 1, new Crumb(albumPage.title(), albumPage.path()));
        crumbs.add(new Crumb("Photo " + photo.number(), null));
        pageModel.apply(model, photoPage, crumbs);

        model.addAttribute("album", album);
        model.addAttribute("photo", photo);
        model.addAttribute("previous", index > 0 ? photos.get(index - 1) : null);
        model.addAttribute("next", index < photos.size() - 1 ? photos.get(index + 1) : null);
        return "pages/gallery/photo";
    }

    private AlbumView findAlbum(String slug) {
        return gallery.album(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static Page albumPage(AlbumView album) {
        return new Page("album", album.path(), album.album().title(), album.album().description(), null,
                "gallery", album.lastUpdated(), List.of());
    }
}
