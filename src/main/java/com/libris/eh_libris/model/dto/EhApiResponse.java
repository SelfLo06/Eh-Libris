package com.libris.eh_libris.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class EhApiResponse {
    private List<EhGalleryMetadata> gmetadata;
}
