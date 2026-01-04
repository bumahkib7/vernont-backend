package com.vernont.api.config

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.http.HttpHeaders
import org.apache.http.HttpHost
import org.apache.http.message.BasicHeader
import org.elasticsearch.client.RestClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.repository.config.EnableReactiveElasticsearchRepositories

@Configuration
@EnableReactiveElasticsearchRepositories(
    basePackages = ["com.vernont.application.search"]
)
@ConditionalOnProperty(name = ["spring.data.elasticsearch.rest.uris"])
class ElasticsearchConfig(
    @Value("\${spring.data.elasticsearch.rest.uris:http://localhost:9200}")
    private val elasticsearchUrl: String
) {

    @Bean
    fun restClient(): RestClient {
        val httpHost = HttpHost.create(elasticsearchUrl)

        return RestClient.builder(httpHost)
            .setDefaultHeaders(
                arrayOf(
                    // Phase 1.3: Enable gzip compression for 30-50% network I/O reduction
                    BasicHeader(HttpHeaders.ACCEPT_ENCODING, "gzip")
                )
            )
            // Phase 1.3: Configure connection timeouts
            .setRequestConfigCallback { requestConfigBuilder ->
                requestConfigBuilder
                    .setConnectTimeout(5000)              // 5s connection timeout
                    .setSocketTimeout(60000)              // 60s socket timeout (for long queries)
                    .setConnectionRequestTimeout(1000)    // 1s timeout to get connection from pool
            }
            // Phase 1.3: Configure connection pooling for better performance
            .setHttpClientConfigCallback { httpClientBuilder ->
                httpClientBuilder
                    .setMaxConnTotal(100)                 // Max 100 total connections
                    .setMaxConnPerRoute(50)               // Max 50 connections per Elasticsearch node
            }
            .build()
    }

    @Bean
    fun elasticsearchClient(
        restClient: RestClient,
        objectMapper: ObjectMapper
    ): ElasticsearchClient {
        // Use JacksonJsonpMapper with Kotlin-aware ObjectMapper
        val transport = RestClientTransport(restClient, JacksonJsonpMapper(objectMapper))
        return ElasticsearchClient(transport)
    }

    @Bean
    fun elasticsearchOperations(elasticsearchClient: ElasticsearchClient): ElasticsearchOperations {
        return ElasticsearchTemplate(elasticsearchClient)
    }

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        // Ignore unknown fields like "_class" from Spring Data ES and keep Kotlin support.
        return ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}
