package com.libris.eh_libris.service;

import com.libris.eh_libris.model.dto.CommentDto;
import com.libris.eh_libris.model.dto.GalleryPageDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public interface ReaderService {
    public List<GalleryPageDto> getGalleryPages(Long gid, String token, int pageIndex) throws IOException;
    public String getTrueImageUrl(String pageUrl) throws IOException;
    List<CommentDto> getGalleryComments(Long gid, String token) throws IOException;
}
