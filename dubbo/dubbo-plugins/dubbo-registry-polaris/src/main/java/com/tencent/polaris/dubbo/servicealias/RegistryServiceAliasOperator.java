package com.tencent.polaris.dubbo.servicealias;

import org.apache.dubbo.common.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shade.polaris.okhttp3.*;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:amyson99@foxmail.com">amyson</a>
 */
public class RegistryServiceAliasOperator {
    public static String KEY_Polaris_Rest_Token = "token";
    public static String KEY_Polaris_Rest_Port = "port";

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryServiceAliasOperator.class);
    static String aliasUrl = null;
    static String token = null;
    static OkHttpClient httpClient = null;
    static MediaType jsonType = MediaType.get("application/json; charset=utf-8");

    public static void setup(URL registryUrl) {
        token = registryUrl.getParameter(KEY_Polaris_Rest_Token);
        String port = registryUrl.getParameter(KEY_Polaris_Rest_Port, "8090");
        if (token != null) {
            token = URLDecoder.decode(token);
            aliasUrl = String.format("http://%s:%s/naming/v1/service/alias", registryUrl.getHost(), port);
            httpClient = new OkHttpClient().newBuilder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS)
                    .writeTimeout(3, TimeUnit.SECONDS).build();
            LOGGER.info("[POLARIS] register dubbo service with alias enabled");
        }
    }

    public static boolean enabled() {
        return token != null;
    }

    public static String getService(URL svrUrl) {
        return enabled() ? ServiceAlias.getApplication(svrUrl) : svrUrl.getServiceInterface();
    }

    public static void saveServiceAlias(ServiceAlias alias) {
        if (!enabled())
            return;

        RequestBody body = RequestBody.create(alias.toJson(), jsonType);
        Request request = new Request.Builder().url(aliasUrl)
                .header("X-Polaris-Token", token)
                .post(body).build();
        try {
            httpClient.newCall(request).execute();
        } catch (IOException ex) {
            LOGGER.error("[POLARIS] save dubbo service alias %s error", alias.getAlias(), ex);
        }
    }
}
