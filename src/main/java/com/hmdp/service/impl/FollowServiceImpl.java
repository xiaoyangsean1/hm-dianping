package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {

        //获取登录用户
        Long userId = UserHolder.getUser().getId();

        String key = "follows:" + userId;

        //1.判断是关注还是取消关注
        if (isFollow) {
            //2.关注，新增数据
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            boolean isSuccess = save(follow);

            if (isSuccess) {
                //把关注的用户id放入redis中的set集合中
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }

        } else {
            //3.取关，删除数据
            //这样写为什么不行
           /* LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Follow::getUserId, userId);
            queryWrapper.eq(Follow::getFollowUserId, followUserId);*/

            QueryWrapper<Follow> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId).eq("follow_user_id", followUserId);
            boolean isSuccess = remove(queryWrapper);

            if (isSuccess) {
                //把关注的用户从redis中的set集合中移出
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        UserDTO user = UserHolder.getUser();
        if(user == null)    return Result.ok(1 == 0);   //用户未登录时默认显示未关注
        //1.获取登录用户id
        Long userId = user.getId();

        //2.查询是否已经关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();

        //3.返回结果
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {

        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;

        //2.求共同关注（求交集）
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null) {
            //无交集
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        //3.根据用户ids查询用户
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());


        return Result.ok(userDTOS);
    }
}
