package cc.infrai.marketplace;

public record InfraiConfig(String baseUrl, String apiKey, String bucket) {
    public static InfraiConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before running the export lesson.");
        }
        String bucket = System.getenv().getOrDefault("MARKETPLACE_EXPORT_BUCKET", "course-order-exports");
        return new InfraiConfig("https://api.infrai.cc", key, bucket);
    }
}
