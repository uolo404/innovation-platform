package com.abajin.innovation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 统一身份认证（二次校验）配置。
 * 用户仍使用同一个用户名/密码表单登录；后端会在本地账号校验通过后，再校验统一身份认证。
 */
@Data
@Component
@ConfigurationProperties(prefix = "unified-auth")
public class UnifiedAuthConfig {
    private Boolean enabled = false;
    private String mode = "LDAP";
    private Ldap ldap = new Ldap();

    @Data
    public static class Ldap {
        private String url;
        private String userDnPattern;
        private String baseDn;
        private String searchFilter = "(uid={0})";

        private String uidAttr = "uid";
        private String nameAttr = "cn";
        private String collegeAttr = "ou";

        private Integer connectTimeoutMs = 3000;
        private Integer readTimeoutMs = 5000;
    }
}

