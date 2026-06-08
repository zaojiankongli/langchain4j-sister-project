package com.zjkl.memory.gallery.mapper;

import com.zjkl.memory.gallery.entity.ConversationMemoryGalleryLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationMemoryGalleryLinkMapper {
    int insert(ConversationMemoryGalleryLink link);
    int deleteByMemoryId(@Param("memoryId") Long memoryId);
    List<ConversationMemoryGalleryLink> selectByMemoryId(@Param("memoryId") Long memoryId);
    ConversationMemoryGalleryLink selectPrimaryByMemoryIdAndGalleryKey(@Param("memoryId") Long memoryId,
                                                                       @Param("galleryKey") String galleryKey);
}
