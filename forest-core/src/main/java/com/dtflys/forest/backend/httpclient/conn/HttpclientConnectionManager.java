package com.dtflys.forest.backend.httpclient.conn;

import com.dtflys.forest.backend.ForestConnectionManager;
import com.dtflys.forest.backend.HttpConnectionConstants;
import com.dtflys.forest.backend.httpclient.HttpClientProvider;
import com.dtflys.forest.config.ForestConfiguration;
import com.dtflys.forest.exceptions.ForestRuntimeException;
import com.dtflys.forest.handler.LifeCycleHandler;
import com.dtflys.forest.http.ForestProxy;
import com.dtflys.forest.http.ForestRequest;
import com.dtflys.forest.utils.StringUtils;
import com.dtflys.forest.utils.TimeUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

/**
 * @author gongjun[jun.gong@thebeastshop.com]
 * @since 2017-04-20 17:23
 */
public class HttpclientConnectionManager implements ForestConnectionManager {
    private DefaultHttpClientProvider defaultHttpClientProvider;
    private PoolingHttpClientConnectionManager tsConnectionManager;

    public HttpclientConnectionManager() {
    }

    @Override
    public void init(ForestConfiguration configuration) {
        try {
            Integer maxConnections = configuration.getMaxConnections() != null ?
                    configuration.getMaxConnections() : HttpConnectionConstants.DEFAULT_MAX_TOTAL_CONNECTIONS;
            Registry<ConnectionSocketFactory> socketFactoryRegistry =
                    RegistryBuilder.<ConnectionSocketFactory>create()
                            .register("https", new ForestSSLConnectionFactory())
                            .register("http", new PlainConnectionSocketFactory())
                            .build();
            tsConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            tsConnectionManager.setMaxTotal(maxConnections);
            tsConnectionManager.setDefaultMaxPerRoute(Integer.MAX_VALUE);
            tsConnectionManager.setValidateAfterInactivity(60);
            defaultHttpClientProvider = new DefaultHttpClientProvider(this);
        } catch (Throwable th) {
            throw new ForestRuntimeException(th);
        }
    }


    public static class DefaultHttpClientProvider implements HttpClientProvider {

        private final HttpclientConnectionManager connectionManager;

        public DefaultHttpClientProvider(HttpclientConnectionManager connectionManager) {
            this.connectionManager = connectionManager;
        }

        @Override
        public HttpClient getClient(ForestRequest request, LifeCycleHandler lifeCycleHandler) {
            HttpClientBuilder builder = HttpClients.custom();
            builder.setConnectionManager(connectionManager.tsConnectionManager);

            RequestConfig.Builder configBuilder = RequestConfig.custom();
            // 超时时间
            Integer timeout = request.getTimeout();
            // 连接超时时间
            Integer connectTimeout = request.connectTimeout();
            // 读取超时时间
            Integer readTimeout = request.readTimeout();

            if (TimeUtils.isNone(connectTimeout)) {
                connectTimeout = timeout;
            }
            if (TimeUtils.isNone(readTimeout)) {
                readTimeout = timeout;
            }
            // 设置请求连接超时时间
            configBuilder.setConnectTimeout(connectTimeout);
            // 设置请求数据传输超时时间
            configBuilder.setSocketTimeout(readTimeout);
            // 设置从连接池获取连接实例的超时
//        configBuilder.setConnectionRequestTimeout(-1);
            // 在提交请求之前 测试连接是否可用
//        configBuilder.setStaleConnectionCheckEnabled(true);
            // 设置Cookie策略
            configBuilder.setCookieSpec(CookieSpecs.IGNORE_COOKIES);
            // 禁止自动重定向
            configBuilder.setRedirectsEnabled(false);


            ForestProxy forestProxy = request.getProxy();
            if (forestProxy != null) {
                HttpHost proxy = new HttpHost(forestProxy.getHost(), forestProxy.getPort());
                if (StringUtils.isNotEmpty(forestProxy.getUsername()) &&
                        StringUtils.isNotEmpty(forestProxy.getPassword())) {
                    CredentialsProvider provider = new BasicCredentialsProvider();
                    provider.setCredentials(
                            new AuthScope(proxy),
                            new UsernamePasswordCredentials(
                                    forestProxy.getUsername(),
                                    forestProxy.getPassword()));
                    builder.setDefaultCredentialsProvider(provider);
                }
                configBuilder.setProxy(proxy);
            }
            RequestConfig requestConfig = configBuilder.build();
            return builder
                    .setDefaultRequestConfig(requestConfig)
                    .disableContentCompression()
                    .build();
        }
    }

    public HttpClient getHttpClient(ForestRequest request, LifeCycleHandler lifeCycleHandler) {
        final String key = "hc;" + request.clientKey();
        final boolean canCacheClient = request.cacheBackendClient();
        if (canCacheClient) {
            final HttpClient cachedClient = request.getRoute().getBackendClient(key);
            if (cachedClient != null) {
                return cachedClient;
            }
        }

        HttpClientProvider provider = defaultHttpClientProvider;
        Object client = request.getBackendClient();
        if (client != null) {
            if (client instanceof HttpClient) {
                return (HttpClient) client;
            }
            if (client instanceof HttpClientProvider) {
                provider = (HttpClientProvider) client;
            } else {
                throw new ForestRuntimeException("[Forest] Backend '" +
                        request.getBackend().getName() +
                        "' does not support client of type '" +
                        client.getClass().getName() + "'");
            }
        }
        final HttpClient httpClient = provider.getClient(request, lifeCycleHandler);
        if (canCacheClient) {
            request.getRoute().cacheBackendClient(key, httpClient);
        }
        return httpClient;
    }

    /**
     * 获取Httpclient连接池管理对象
     *
     * @return {@link PoolingHttpClientConnectionManager}实例
     */
    @Deprecated
    public static PoolingHttpClientConnectionManager getPoolingHttpClientConnectionManager() {
        return null;
    }

}
