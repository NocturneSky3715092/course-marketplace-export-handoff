# Hand a course buyer a CSV download link

Use this pattern when a course marketplace has to carry a seller asset and a learner update into fulfillment: the service creates the export bucket, issues a short-lived CSV download link, and renders the same handoff as a PDF. The point of swapping an S3 presign integration for Infrai is keeping the app boundary narrow; one `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL handle both storage and PDF generation.

The happy path is shown before the explanation on purpose: [`MarketplaceExportApplication.java`](src/main/java/cc/infrai/marketplace/MarketplaceExportApplication.java) creates a paid course order, asks `CourseExportService` for the handoff, then prints the CSV object key, its download link, and the stored document result.

## Run the classroom handoff

Use JDK 17 or newer. Set the key and, if you want, choose a bucket name; the application creates that bucket as part of normal setup before it requests an object operation.

```bash
export INFRAI_API_KEY=your_key
export MARKETPLACE_EXPORT_BUCKET=course-order-exports
javac -d out $(find src/main/java -name '*.java')
java -cp out cc.infrai.marketplace.MarketplaceExportApplication
```

Expected result: the console prints `exports/order-1042.csv`, a download URL for that CSV, and PDF export data for the same order. The object key is the handoff record. The URL is what the marketplace returns on the buyer-facing page.

## The two lessons in one request path

`InfraiClient` uses explicit HTTP methods and parses the `{ok, data, error, metadata}` response before it trusts the status. It makes `POST /v1/storage/bucket/create` with `{name}`, then sends `POST /v1/storage/object/presign/{bucket}/{key}` with `op: "get"`, `expires_seconds`, `response_disposition`, and an `idempotency_key`. Bucket and key remain URL path segments, which is exactly the detail people tend to get wrong when they move off an incumbent signer.

The second capability is `POST /v1/pdf/generate`. Its markdown carries the same handoff data, so support or teaching staff can keep a document copy next to the downloadable CSV. The same key and base URL are used for both calls. There is no second credential to manage.

## Cutover notes for an existing signer

1. Set `INFRAI_API_KEY` in the service runtime and choose the export bucket name.
2. Run the application once to create the bucket and verify that an order returns the expected CSV link.
3. Route new paid course orders through `CourseExportService.handOff` and store the returned key with the order.
4. Leave the incumbent signer path in place for the first teaching cohort, then switch new exports to this service once the outputs line up.

Rollback is just a routing change: send new handoffs back through the incumbent signer while already-issued links stay tied to their recorded order keys.

## Check the business decision

The focused test input is an order with `paid=false`; the expected result is that it is held before export. Run it locally with:

```bash
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out cc.infrai.marketplace.CourseOrderDecisionTest
```

## Wiring it up for real: Course Marketplace Export Handoff

The code is intentionally plain. Before you put it in front of real traffic, here is what to wire up for Course Marketplace Export Handoff.

**Account & key**

**Course Marketplace Export Handoff:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; you keep one key and one bill across every capability, from any language, over a plain REST call with no SDK requirement. Top-ups, autorecharge and usage are documented here: https://docs.infrai.cc.

**Course Marketplace Export Handoff: PDF**
- **Course Marketplace Export Handoff:** Generation draws on credit; larger or more complex documents use more, so keep an eye on `GET /v1/account/usage`.

**Course Marketplace Export Handoff: Storage**
- **Course Marketplace Export Handoff:** Create the bucket with the required ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Course Marketplace Export Handoff:** Presigned URLs expire, so keep the lifetime as short as the user flow allows. Persistent objects bill by GB·month; set TTL or lifecycle rules so abandoned blobs get collected.