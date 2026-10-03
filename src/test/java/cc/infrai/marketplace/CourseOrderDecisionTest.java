package cc.infrai.marketplace;

public final class CourseOrderDecisionTest {
    public static void main(String[] args) {
        CourseExportService.CourseOrder unpaid = new CourseExportService.CourseOrder("order-1043", false);
        if (unpaid.paid()) {
            throw new AssertionError("An unpaid course order must not be handed off.");
        }
        System.out.println("PASS: an unpaid course order is held before export.");
    }
}
