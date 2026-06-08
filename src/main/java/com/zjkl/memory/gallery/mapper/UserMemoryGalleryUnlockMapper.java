package com.zjkl.memory.gallery.mapper;

import com.zjkl.memory.gallery.domain.vo.GalleryUnlockVO;
import com.zjkl.memory.gallery.entity.UserMemoryGalleryUnlock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMemoryGalleryUnlockMapper {
    int insert(UserMemoryGalleryUnlock unlock);
    UserMemoryGalleryUnlock selectByUserIdAndGalleryKey(@Param("userId") String userId,
                                                        @Param("galleryKey") String galleryKey);
    List<GalleryUnlockVO> selectUnlockViewsByUserId(@Param("userId") String userId);
    GalleryUnlockVO selectUnlockViewByUserIdAndGalleryKey(@Param("userId") String userId,
                                                          @Param("galleryKey") String galleryKey);
}
