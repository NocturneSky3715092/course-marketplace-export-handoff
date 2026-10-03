package cc.infrai.marketplace;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class InfraiClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final InfraiConfig config;

    public InfraiClient(InfraiConfig config) {
        this.config = config;
    }

    public void createBucket() throws IOException, InterruptedException {
        call("POST", "/v1/storage/bucket/create", "{\"name\":\"" + json(config.bucket()) + "\"}");
    }

    public String createDownloadLink(String key, String requestId) throws IOException, InterruptedException {
        String body = "{\"op\":\"get\",\"expires_seconds\":900,\"response_disposition\":\"attachment\",\"idempotency_key\":\""
                + json(requestId) + "\"}";
        return requiredString(call("POST", "/v1/storage/object/presign/" + segment(config.bucket()) + "/" + segment(key), body), "url");
    }

    public void uploadCsv(String key, String csv, String requestId) throws IOException, InterruptedException {
        String body = "{\"op\":\"put\",\"content_type\":\"text/csv; charset=utf-8\",\"idempotency_key\":\""
                + json(requestId) + "\"}";
        String uploadUrl = requiredString(
                call("POST", "/v1/storage/object/presign/" + segment(config.bucket()) + "/" + segment(key), body),
                "url");
        HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "text/csv; charset=utf-8")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(csv.getBytes(StandardCharsets.UTF_8)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Object upload response status " + response.statusCode());
        }
    }

    public String renderExportDocument(String markdown) throws IOException, InterruptedException {
        String body = "{\"markdown\":\"" + json(markdown) + "\",\"page_size\":\"A4\",\"orientation\":\"portrait\",\"store\":true}";
        return call("POST", "/v1/pdf/generate", body);
    }

    private String call(String method, String path, String body) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (response.statusCode() == 429 && attempt < 2) {
                pause(response.headers().firstValue("Retry-After").orElse(null), attempt);
                continue;
            }
            if (envelope.contains("\"ok\":true")) {
                return data(envelope);
            }
            if (envelope.contains("\"ok\":false")) {
                throw new InfraiException("Infrai rejected the request: " + envelope);
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport response status " + response.statusCode());
            }
            throw new IOException("Response was not an Infrai envelope.");
        }
        throw new IOException("Rate limit retries exhausted.");
    }

    private static void pause(String retryAfter, int attempt) throws InterruptedException {
        long millis = retryAfter == null ? 250L << attempt : Long.parseLong(retryAfter) * 1000L;
        Thread.sleep(millis);
    }

    private static String data(String envelope) {
        int start = envelope.indexOf("\"data\":");
        if (start < 0) throw new InfraiException("Successful envelope did not include data.");
        int valueStart = start + 7;
        int end = envelope.lastIndexOf(",\"metadata\"");
        return end > valueStart ? envelope.substring(valueStart, end) : envelope.substring(valueStart, envelope.length() - 1);
    }

    private static String requiredString(String data, String field) {
        String marker = "\"" + field + "\":\"";
        int start = data.indexOf(marker);
        if (start < 0) throw new InfraiException("Response data did not include " + field + ".");
        int valueStart = start + marker.length();
        int end = data.indexOf('"', valueStart);
        if (end < 0) throw new InfraiException("Response data contained an incomplete " + field + ".");
        return data.substring(valueStart, end);
    }

    private static String segment(String value) {
        return value.replace(" ", "%20").replace("/", "%2F");
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    public static final class InfraiException extends RuntimeException {
        public InfraiException(String message) { super(message); }
    }
}
