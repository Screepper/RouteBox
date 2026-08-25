package com.routebox.configuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import com.routebox.common.constants.ConfConstants;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * @Description Configuration for Elasticsearch 9.x client
 * @Date 2025/11/24
**/
@Configuration
@DependsOn("confConstants")
public class ElasticsearchConfig {

    /**
     * @Description Build Rest5Client with custom timeout configuration
     * @Date 2025/11/24
     **/
    @Bean
    public Rest5Client restClient() {
        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(150000, TimeUnit.MILLISECONDS));
        CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom()
                .setDefaultRequestConfig(requestConfigBuilder.build())
                .build();
        // Build Rest5Client with custom timeout configuration
        String cred = Base64.getEncoder().encodeToString((
                ConfConstants.esUsername() + ":" + ConfConstants.esPassword()
        ).getBytes());
        Rest5ClientBuilder builder = Rest5Client
                .builder(new HttpHost(ConfConstants.esIp(), ConfConstants.esPort()))
                .setHttpClient(httpclient)
                .setDefaultHeaders(new Header[]{new BasicHeader("Authorization", "Basic " + cred)});

        return builder.build();
    }


    /**
     * @Description Build ElasticsearchClient with custom Rest5Client
     * @Date 2025/11/24
     **/
    @Bean
    public ElasticsearchClient elasticsearchClient(Rest5Client restClient) {
        Rest5ClientTransport transport = new Rest5ClientTransport(
                restClient,
                new JacksonJsonpMapper()
        );

        return new ElasticsearchClient(transport);
    }
}