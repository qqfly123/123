package com.spark.app.web.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spark.app.web.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户服务 - 管理用户注册、认证，数据持久化到 JSON 文件。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();

    @Value("${spark.data.dir}")
    private String dataDir;

    public UserService() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void init() {
        loadUsers();
        if (users.isEmpty()) {
            register("admin", "admin123", "管理员");
            log.info("已创建默认管理员账户: admin / admin123");
        }
    }

    /**
     * 注册新用户。
     *
     * @return 注册成功的用户，若用户名已存在返回 null
     */
    public User register(String username, String password, String displayName) {
        if (username == null || username.trim().isEmpty()
                || password == null || password.isEmpty()) {
            return null;
        }
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        if (users.values().stream()
                .anyMatch(u -> u.getUsername().equals(normalizedUsername))) {
            return null;
        }

        String salt = generateSalt();
        String hash = hashPassword(password, salt);

        User user = new User();
        user.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        user.setUsername(normalizedUsername);
        user.setPasswordHash(hash);
        user.setSalt(salt);
        user.setDisplayName(displayName != null && !displayName.trim().isEmpty()
                ? displayName.trim() : normalizedUsername);
        user.setCreatedAt(LocalDateTime.now());

        users.put(user.getId(), user);
        saveUsers();
        return user;
    }

    /**
     * 验证用户凭据。
     *
     * @return 验证通过的用户，否则返回 null
     */
    public User authenticate(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        return users.values().stream()
                .filter(u -> u.getUsername().equals(normalizedUsername))
                .filter(u -> u.getPasswordHash().equals(hashPassword(password, u.getSalt())))
                .findFirst()
                .orElse(null);
    }

    private String generateSalt() {
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private File getUsersFile() {
        return new File(dataDir, "users.json");
    }

    private void loadUsers() {
        File file = getUsersFile();
        if (!file.exists()) {
            return;
        }
        try {
            List<User> list = mapper.readValue(file, new TypeReference<List<User>>() {});
            list.forEach(u -> users.put(u.getId(), u));
            log.info("已加载 {} 个用户", users.size());
        } catch (IOException e) {
            log.warn("加载用户数据失败: {}", e.getMessage());
        }
    }

    private void saveUsers() {
        try {
            mapper.writeValue(getUsersFile(), new ArrayList<>(users.values()));
        } catch (IOException e) {
            log.error("保存用户数据失败: {}", e.getMessage());
        }
    }
}
