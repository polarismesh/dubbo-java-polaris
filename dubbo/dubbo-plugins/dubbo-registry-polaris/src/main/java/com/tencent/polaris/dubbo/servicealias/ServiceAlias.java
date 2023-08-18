package com.tencent.polaris.dubbo.servicealias;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import org.apache.dubbo.common.URL;

/**
 * @author <a href="mailto:amyson99@foxmail.com">amyson</a>
 */
public class ServiceAlias {
    private String service;
    private String namespace;
    private String alias;
    @JSONField(name = "alias_namespace")
    private String aliasNamespace;

    public ServiceAlias(URL url) {
        this(url, "default");
    }

    public ServiceAlias(URL url, String namespace) {
        this(url, namespace, namespace);
    }

    public ServiceAlias(URL url, String namespace, String aliasNamespace) {
        this.service = getApplication(url);
        this.namespace = namespace;
        this.alias = url.getServiceInterface();
        this.aliasNamespace = aliasNamespace;
    }

    public static String getApplication(URL url) {
        return url.getParameter("application");
    }

    public String toJson() {
        return JSON.toJSONString(this);
    }

    public String getService() {
        return service;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getAlias() {
        return alias;
    }

    public String getAliasNamespace() {
        return aliasNamespace;
    }
}
