package com.zjkl.user.service;


import com.zjkl.user.domain.dto.UserProfileUpdateDTO;
import com.zjkl.user.domain.vo.UserProfileVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户资料服务接口
 */
public interface UserProfileService {
    
    /**
     * 获取用户完整资料
     */
    UserProfileVO getProfile(String userId);

    /**
     * 获取聊天所需的轻量用户信息（仅 username、hobbies、bio），
     * 避免每次聊天都执行完整的 6 次 DB 查询。
     *
     * @return String[3]：[0]=username, [1]=hobbies, [2]=userProfile(bio)；
     *         用户不存在时返回 null
     */
    String[] getProfileForChat(String userId);
    
    /**
     * 更新用户资料（综合）
     */
    void updateProfile(String userId, UserProfileUpdateDTO dto);
    
    /**
     * 更新基本信息（用户名、性别）
     */
    void updateBasic(String userId, String username, Integer gender);
    
    /**
     * 更新兴趣爱好
     */
    void updateHobbies(String userId, String hobbies);
    
    /**
     * 更新 AI 身份类型
     */
    void updateAiType(String userId, Integer aiType);

    /**
     * 上传头像
     */
    String uploadAvatar(String userId, MultipartFile file);
}
