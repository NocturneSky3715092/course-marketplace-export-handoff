package cc.infrai.marketplace;

import java.io.IOException;

public final class CourseExportService {
    private final InfraiClient infrai;

    public CourseExportService(InfraiClient infrai) {
        this.infrai = infrai;
    }

    public ExportHandoff handOff(SellerAsset asset, BuyerUpdate buyer, CourseOrder order) throws IOException, InterruptedException {
        if (!order.paid()) {
            throw new IllegalArgumentException("A course export is handed off after payment.");
        }
        infrai.createBucket();
        String csvKey = "exports/" + order.orderId() + ".csv";
        String csv = "course_title,asset,buyer_email,lesson_update\n" + asset.courseTitle() + "," + asset.assetName()
                + "," + buyer.email() + "," + buyer.lessonNote();
        infrai.uploadCsv(csvKey, csv, "handoff-upload-" + order.orderId());
        String downloadUrl = infrai.createDownloadLink(csvKey, "handoff-" + order.orderId());
        String document = infrai.renderExportDocument("# Course handoff\n\nOrder: " + order.orderId() + "\n\n" + csv);
        return new ExportHandoff(csvKey, csv, downloadUrl, document);
    }

    public record SellerAsset(String courseTitle, String assetName) {}
    public record BuyerUpdate(String email, String lessonNote) {}
    public record CourseOrder(String orderId, boolean paid) {}
    public record ExportHandoff(String csvKey, String csv, String downloadUrl, String documentData) {}
}
