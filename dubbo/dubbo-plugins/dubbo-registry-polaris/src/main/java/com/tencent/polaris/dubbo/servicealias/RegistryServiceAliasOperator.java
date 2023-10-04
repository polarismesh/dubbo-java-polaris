package com.tencent.polaris.dubbo.servicealias;

import org.apache.dubbo.common.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URLConnection;
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

    public static URL setup(URL registryUrl) {
        token = registryUrl.getParameter(KEY_Polaris_Rest_Token);
        if (token == null)
            return registryUrl;

        String port = registryUrl.getParameter(KEY_Polaris_Rest_Port, "8090");
        token = URLDecoder.decode(token);
        aliasUrl = String.format("http://%s:%s/naming/v1/service/alias", registryUrl.getHost(), port);
        LOGGER.info("[POLARIS] register dubbo service with alias enabled");
        return registryUrl.removeParameter(KEY_Polaris_Rest_Token);
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

        try {
            post(aliasUrl, token, alias.toJson());
        } catch (Exception ex) {
            LOGGER.error("[POLARIS] save dubbo service alias %s error", alias.getAlias(), ex);
        }
    }

    public static void post(String polarisUrl, String token, String body) throws Exception {
        PrintWriter out = null;
        BufferedReader in = null;
        try {
            java.net.URL url = new java.net.URL(polarisUrl);
            URLConnection conn = url.openConnection();

            conn.setRequestProperty("X-Polaris-Token", token);
            conn.setConnectTimeout(3 * 1000);
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible;)");
            conn.setRequestProperty("Content-Type", "application/json"); // 设置内容类型

            conn.setDoOutput(true);
            conn.setDoInput(true);

            out = new PrintWriter(conn.getOutputStream());
            out.print(body);
            out.flush();

            in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            in.readLine();
        } finally {
            if (out != null)
                out.close();
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) {}
            }
        }
    }
}
