package com.system.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginVo {
    
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("refresh_token")
    private String refreshToken;
    
    @JsonProperty("expire_in")
    private Long expireIn;
    
    @JsonProperty("refresh_expire_in")
    private Long refreshExpireIn;
    
    private String tokenType = "Bearer";
    
    private UserInfo user;

    public LoginVo() {
    }

    public LoginVo(String accessToken, Long expireIn, UserInfo user) {
        this.accessToken = accessToken;
        this.expireIn = expireIn;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getExpireIn() {
        return expireIn;
    }

    public void setExpireIn(Long expireIn) {
        this.expireIn = expireIn;
    }

    public Long getRefreshExpireIn() {
        return refreshExpireIn;
    }

    public void setRefreshExpireIn(Long refreshExpireIn) {
        this.refreshExpireIn = refreshExpireIn;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
    }

    public static class UserInfo {
        private Long userId;
        private String username;
        private String nickName;
        private String email;
        private String avatar;

        public UserInfo() {
        }

        public UserInfo(Long userId, String username, String nickName, String email, String avatar) {
            this.userId = userId;
            this.username = username;
            this.nickName = nickName;
            this.email = email;
            this.avatar = avatar;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getNickName() {
            return nickName;
        }

        public void setNickName(String nickName) {
            this.nickName = nickName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getAvatar() {
            return avatar;
        }

        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }
    }
}
