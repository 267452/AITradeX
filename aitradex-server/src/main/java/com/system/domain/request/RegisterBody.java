package com.system.domain.request;

public class RegisterBody {

    private String username;
    private String password;
    private String confirmPassword;
    private String nickName;
    private String email;

    public RegisterBody() {
    }

    public RegisterBody(String username, String password, String confirmPassword, String nickName, String email) {
        this.username = username;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.nickName = nickName;
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
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
}
