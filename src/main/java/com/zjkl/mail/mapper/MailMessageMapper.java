package com.zjkl.mail.mapper;

import com.zjkl.mail.entity.MailMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MailMessageMapper {

    List<MailMessage> selectByUserId(@Param("userId") String userId);

    MailMessage selectById(@Param("id") String id);

    int insert(MailMessage mail);

    int markAsRead(@Param("id") String id, @Param("userId") String userId);

    int markAllAsRead(@Param("userId") String userId);

    int insertWelcomeMails(@Param("userId") String userId);
}
