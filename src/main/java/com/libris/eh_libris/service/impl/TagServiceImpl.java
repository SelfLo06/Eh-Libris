package com.libris.eh_libris.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.libris.eh_libris.dao.TagMapper;
import com.libris.eh_libris.model.entity.Tag;
import com.libris.eh_libris.service.TagService;
import org.springframework.stereotype.Service;

@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService{
}
