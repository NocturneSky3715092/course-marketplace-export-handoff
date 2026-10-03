package cc.infrai.marketplace;

public final class MarketplaceExportApplication {
    public static void main(String[] args) throws Exception {
        InfraiConfig config = InfraiConfig.fromEnvironment();
        CourseExportService service = new CourseExportService(new InfraiClient(config));
        CourseExportService.ExportHandoff handoff = service.handOff(
                new CourseExportService.SellerAsset("Practical Algebra", "worksheet-07.pdf"),
                new CourseExportService.BuyerUpdate("learner@example.com", "Lesson 7 released"),
                new CourseExportService.CourseOrder("order-1042", true));
        System.out.println("CSV key: " + handoff.csvKey());
        System.out.println("Download link: " + handoff.downloadUrl());
        System.out.println("PDF export data: " + handoff.documentData());
    }
}
