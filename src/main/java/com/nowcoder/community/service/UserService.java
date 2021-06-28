package com.nowcoder.community.service;

import com.nowcoder.community.dao.LoginTicketMapper;
import com.nowcoder.community.dao.UserMapper;
import com.nowcoder.community.entity.LoginTicket;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.MailClient;
import com.nowcoder.community.util.RedisKeyUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class UserService implements CommunityConstant {

    @Resource
    private UserMapper userMapper;

    @Autowired
    private MailClient mailClient;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private RedisTemplate redisTemplate;

//    @Resource
//    private LoginTicketMapper loginTicketMapper;

    // 项目路径
    @Value("${community.path.domain}")
    private String domain;

    // 项目名
    @Value("${server.servlet.context-path}")
    private String contextPath;

    public User findUserById(int id) {

        User user = getCache(id);
        if (user == null){
            initCache(id);
        }
        return user;
    }

    // 返回值可以由自己决定，这里由自己决定
    public Map<String, Object> register(User user) {
        Map<String, Object> map = new HashMap<>();

        // 空值处理
        if (user == null) {
            throw new IllegalArgumentException("参数不能为空!");
        }
        if (StringUtils.isBlank(user.getUsername())) {
            map.put("usernameMsg", "账号不能为空!");
            return map;
        }
        if (StringUtils.isBlank(user.getPassword())) {
            map.put("passwordMsg", "密码不能为空!");
            return map;
        }
        if (StringUtils.isBlank(user.getEmail())) {
            map.put("emailMsg", "邮箱不能为空!");
            return map;
        }

        // 验证账号
        User u = userMapper.selectByName(user.getUsername());
        if (u != null) {
            map.put("usernameMsg", "该账号已存在!");
            return map;
        }

        // 验证邮箱
        u = userMapper.selectByEmail(user.getEmail());
        if (u != null) {
            map.put("emailMsg", "该邮箱已被注册!");
            return map;
        }

        // 注册用户
        user.setSalt(CommunityUtil.generateUUID().substring(0, 5));
        user.setPassword(CommunityUtil.md5(user.getPassword() + user.getSalt()));
        user.setType(0);
        user.setStatus(0);
        user.setActivationCode(CommunityUtil.generateUUID());
        // 设置随机的头像，牛客网有1000个随机的头像
        user.setHeaderUrl(String.format("http://images.nowcoder.com/head/%dt.png", new Random().nextInt(1000)));
        user.setCreateTime(new Date());
        userMapper.insertUser(user);

        // 激活邮件
        Context context = new Context();
        context.setVariable("email", user.getEmail());
        // http://localhost:8080/community/activation/101/code
        String url = domain + contextPath + "/activation/" + user.getId() + "/" + user.getActivationCode();
        context.setVariable("url", url);
        String content = templateEngine.process("/mail/activation", context);
        mailClient.sendMail(user.getEmail(), "激活账号", content);

        return map;
    }

    public int activation(int userId, String code) {
        User user = userMapper.selectById(userId);
        if (user.getStatus() == 1) {
            return ACTIVATION_REPEAT;
        } else if (user.getActivationCode().equals(code)) {
            userMapper.updateStatus(userId, 1);

            clearCache(userId); // 更新完状态后要把缓存清理掉

            return ACTIVATION_SUCCESS;
        } else {
            return ACTIVATION_FAILURE;
        }
    }


    // 实现登录功能
    public Map<String, Object> login(String username, String password, int expiredSeconds){
        Map<String, Object> map = new HashMap<>();

        // 空值处理
        if(StringUtils.isBlank(username)){
            map.put("usernameMsg","账号不能为空!");
            return map;
        }
        if(StringUtils.isBlank(password)){
            map.put("usernameMsg","密码不能为空!");
            return map;
        }

        // 验证账号
        User user = userMapper.selectByName(username);
        if(user == null){
            map.put("usernameMsg","该账号不存在!");
            return map;
        }

        if(user.getStatus() == 0){
            map.put("usernameMsg","该账号未激活!");
            return map;
        }

        // 验证密码
        password = CommunityUtil.md5(password+user.getSalt());
        if(!user.getPassword().equals(password)){
            map.put("passwordMsg", "密码不正确！");
            return map;
        }

        // 生成登录凭证
        LoginTicket loginTicket = new LoginTicket();
        loginTicket.setUserId(user.getId());
        loginTicket.setTicket(CommunityUtil.generateUUID());
        loginTicket.setStatus(0);
        loginTicket.setExpired(new Date(System.currentTimeMillis() + expiredSeconds*1000));
//        loginTicketMapper.insertLoginTicket(loginTicket);

        String redisKey = RedisKeyUtil.getTicketKey(loginTicket.getTicket());
        redisTemplate.opsForValue().set(redisKey,loginTicket); // 这一句会把loginTicket序列化为一个字符串


        map.put("ticket",loginTicket.getTicket());
        return map;
    }

    // 退出
    public void logout(String ticket){
//        loginTicketMapper.updateStatus(ticket,1);
        String redisKey = RedisKeyUtil.getTicketKey(ticket);
        // 把凭证拿去出来
        LoginTicket loginTicket = (LoginTicket)redisTemplate.opsForValue().get(redisKey);
        // 改变登录凭证的状态
        loginTicket.setStatus(1);
        // 把凭证放回去
        redisTemplate.opsForValue().set(redisKey,loginTicket);
    }

    // 根据凭证本身去查找凭证类的所有信息
    public LoginTicket findLoginTicket(String ticket){
//        return loginTicketMapper.selectByTicket(ticket);
        String redisKey = RedisKeyUtil.getTicketKey(ticket);
        return (LoginTicket) redisTemplate.opsForValue().get(redisKey);
    }

    // 修改头像，后台就是修改头像的路径地址
    public int updateHeader(int userId, String headerUrl){
//        return userMapper.updateHeader(userId,headerUrl);
        int row = userMapper.updateHeader(userId,headerUrl);
        clearCache(userId);
        return row;
    }


    // 根据用户名来查找用户
    public User findUserByName(String userName){
        return userMapper.selectByName(userName);
    }


    /**
     * 优先从缓存中取值
     * @param userId
     * @return
     */
    private User getCache(int userId){
        String redisKey = RedisKeyUtil.getUserKey(userId);
        return (User) redisTemplate.opsForValue().get(redisKey);
    }

    /**
     * 取不到时初始化缓存数据
     * @param userId
     * @return
     */
    private User initCache(int userId){
        // 查询数据库端的数据
        User user = userMapper.selectById(userId);

        String redisKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.opsForValue().set(redisKey,user,3600, TimeUnit.SECONDS);
        return user;
    }

    /**
     * 数据变更时清除缓存数据
     * @param userId
     */
    private void clearCache(int userId){
        String redisKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.delete(redisKey);
    }

}
