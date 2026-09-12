package com.chanter.media.infra;

import com.chanter.media.application.PrivateResourceStorage;
import com.chanter.media.application.ResourceLifecycle;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

@Component
@ConditionalOnProperty(name = "chanter.media.storage-backend", havingValue = "s3")
public class S3PrivateResourceStorage implements PrivateResourceStorage {
    private final S3Client client;
    private final String bucket;
    private final ResourceLifecycle lifecycle;

    public S3PrivateResourceStorage(ResourceLifecycle lifecycle,
            @Value("${chanter.media.s3.endpoint}") String endpoint,
            @Value("${chanter.media.s3.region}") String region,
            @Value("${chanter.media.s3.bucket}") String bucket,
            @Value("${chanter.media.s3.access-key}") String accessKey,
            @Value("${chanter.media.s3.secret-key}") String secretKey,
            @Value("${chanter.media.s3.allow-local-http:false}") boolean localHttp) {
        URI uri;
        try { uri = URI.create(endpoint); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid private S3 configuration"); }
        boolean local = localHttp && "http".equals(uri.getScheme()) && java.util.Set.of("127.0.0.1", "localhost").contains(uri.getHost());
        if ((!"https".equals(uri.getScheme()) && !local) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || !java.util.Set.of("", "/").contains(uri.getPath())
                || !bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") || accessKey.isBlank() || secretKey.isBlank() || region.isBlank()) {
            throw new IllegalArgumentException("Invalid private S3 configuration");
        }
        this.lifecycle = lifecycle; this.bucket = bucket;
        this.client = S3Client.builder().endpointOverride(uri).region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(15)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(config -> config.retryPolicy(RetryPolicy.none()).apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(20))).build();
    }
    @Override public String backend() { return "s3"; }
    @Override public void put(String key, Path content, String sha256) throws IOException {
        PrivateResourceStorage.requireKey(key);
        try {
            String md5 = Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(Files.readAllBytes(content)));
            lifecycle.countRequest(false);
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).ifNoneMatch("*").contentMD5(md5)
                    .contentType("application/octet-stream").metadata(Map.of("sha256", sha256)).build(), RequestBody.fromFile(content));
        } catch (Exception exception) { throw failure(); }
    }
    @Override public InputStream open(String key) throws IOException {
        PrivateResourceStorage.requireKey(key);
        lifecycle.countRequest(false);
        try { return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build()); }
        catch (Exception exception) { throw failure(); }
    }
    @Override public void delete(String key) throws IOException {
        PrivateResourceStorage.requireKey(key);
        lifecycle.countRequest(true);
        try { client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()); }
        catch (S3Exception exception) { if (exception.statusCode() != 404) throw failure(); }
        catch (Exception exception) { throw failure(); }
    }
    @Override public Page list(String cursor) throws IOException {
        lifecycle.countRequest(false);
        try {
            var result = client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).prefix(PREFIX).maxKeys(1000).continuationToken(cursor).build());
            return new Page(result.contents().stream().map(object -> new ObjectInfo(object.key(), object.lastModified())).toList(),
                    Boolean.TRUE.equals(result.isTruncated()) ? result.nextContinuationToken() : null);
        } catch (Exception exception) { throw failure(); }
    }
    private static IOException failure() { return new IOException("Private object storage is unavailable"); }
    @PreDestroy public void close() { client.close(); }
}
