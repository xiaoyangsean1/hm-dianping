package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-24
 */
public interface IUserInfoService extends IService<UserInfo> {

    /**
     * 发送验证码，并将其保存到session中
     * @param phone
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpSession session);
}
