package com.zjkl.memory.gallery.mapper;

import com.zjkl.memory.gallery.entity.MemoryGalleryDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemoryGalleryDefinitionMapper {
    List<MemoryGalleryDefinition> selectEnabledDefinitions();
    MemoryGalleryDefinition selectByGalleryKey(@Param("galleryKey") String galleryKey);
}
