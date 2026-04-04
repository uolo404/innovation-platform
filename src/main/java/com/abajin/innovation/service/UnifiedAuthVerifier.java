package com.abajin.innovation.service;

import com.abajin.innovation.config.UnifiedAuthConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.text.MessageFormat;
import java.util.Hashtable;

/**
 * 统一身份认证校验器（目前支持 LDAP）。
 */
@Service
public class UnifiedAuthVerifier {

    @Autowired
    private UnifiedAuthConfig config;

    public boolean isEnabled() {
        return config.getEnabled() != null && config.getEnabled();
    }

    public UnifiedIdentity verify(String username, String password) {
        if (!isEnabled()) {
            return UnifiedIdentity.disabled();
        }

        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new RuntimeException("用户名或密码错误");
        }

        String mode = config.getMode() == null ? "" : config.getMode().trim().toUpperCase();
        if (!"LDAP".equals(mode)) {
            throw new RuntimeException("统一身份认证未配置或模式不支持");
        }

        return verifyByLdap(username, password);
    }

    private UnifiedIdentity verifyByLdap(String username, String password) {
        UnifiedAuthConfig.Ldap ldap = config.getLdap();
        if (ldap == null || ldap.getUrl() == null || ldap.getUrl().isEmpty()) {
            throw new RuntimeException("统一身份认证未配置");
        }
        if (ldap.getUserDnPattern() == null || ldap.getUserDnPattern().isEmpty()) {
            throw new RuntimeException("统一身份认证未配置");
        }

        String userDn = MessageFormat.format(ldap.getUserDnPattern(), username);

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldap.getUrl());
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, userDn);
        env.put(Context.SECURITY_CREDENTIALS, password);
        if (ldap.getConnectTimeoutMs() != null) {
            env.put("com.sun.jndi.ldap.connect.timeout", ldap.getConnectTimeoutMs().toString());
        }
        if (ldap.getReadTimeoutMs() != null) {
            env.put("com.sun.jndi.ldap.read.timeout", ldap.getReadTimeoutMs().toString());
        }

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);

            Attributes attributes = fetchUserAttributes(ctx, username, userDn);
            String uid = getAttribute(attributes, ldap.getUidAttr());
            String name = getAttribute(attributes, ldap.getNameAttr());
            String college = getAttribute(attributes, ldap.getCollegeAttr());

            UnifiedIdentity identity = new UnifiedIdentity();
            identity.setEnabled(true);
            identity.setUid(uid != null && !uid.isEmpty() ? uid : username);
            identity.setName(name);
            identity.setCollegeName(college);
            return identity;
        } catch (NamingException e) {
            throw new RuntimeException("用户名或密码错误");
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    private Attributes fetchUserAttributes(DirContext ctx, String username, String userDn) throws NamingException {
        UnifiedAuthConfig.Ldap ldap = config.getLdap();

        String baseDn = ldap.getBaseDn();
        String filter = ldap.getSearchFilter();
        if (baseDn != null && !baseDn.isEmpty() && filter != null && !filter.isEmpty()) {
            String realFilter = MessageFormat.format(filter, username);
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setCountLimit(1);

            NamingEnumeration<SearchResult> results = ctx.search(baseDn, realFilter, controls);
            if (results.hasMore()) {
                SearchResult sr = results.next();
                return sr.getAttributes();
            }
        }

        // fallback 1: 直接读取 DN 对应条目的属性
        if (userDn != null && !userDn.isEmpty()) {
            try {
                return ctx.getAttributes(userDn);
            } catch (Exception ignored) {
                // ignore
            }
        }

        // fallback 2: 仅凭 bind 成功视为通过，不强依赖属性读取
        return null;
    }

    private String getAttribute(Attributes attributes, String attrName) throws NamingException {
        if (attributes == null || attrName == null || attrName.isEmpty()) {
            return null;
        }
        Attribute attr = attributes.get(attrName);
        if (attr == null || attr.size() == 0) {
            return null;
        }
        Object value = attr.get(0);
        return value == null ? null : value.toString();
    }

    public static class UnifiedIdentity {
        private boolean enabled;
        private String uid;
        private String name;
        private String collegeName;

        public static UnifiedIdentity disabled() {
            UnifiedIdentity i = new UnifiedIdentity();
            i.enabled = false;
            return i;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUid() {
            return uid;
        }

        public void setUid(String uid) {
            this.uid = uid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCollegeName() {
            return collegeName;
        }

        public void setCollegeName(String collegeName) {
            this.collegeName = collegeName;
        }
    }
}
