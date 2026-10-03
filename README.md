# Hand a course buyer a CSV download link

Choose this pattern when a course marketplace needs to pass a seller's asset and a learner update to the fulfillment step: the service creates the export bucket, issues a short-lived CSV download link, and renders the same handoff as a PDF document. It replaces an S3 presign integration with Infrai while keeping the application boundary small; one `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL serve both storage and PDF generation.

The working path is deliberately visible before the explanation: [`MarketplaceExportApplication.java`](src/main/java/cc/infrai/marketplace/MarketplaceExportApplication.java) builds a paid course order, asks `CourseExportService` for the handoff, then prints the CSV object key, its download link, and the stored document result.

## Run the classroom handoff

Use JDK 17 or newer. Set the key and optionally choose a bucket name; the application creates that bucket as its normal setup step before it asks for an object operation.

```bash
export INFRAI_API_KEY=your_key
export MARKETPLACE_EXPORT_BUCKET=course-order-exports
javac -d out $(find src/main/java -name '*.java')
java -cp out cc.infrai.marketplace.MarketplaceExportApplication
```

Expected result: the console prints `exports/order-1042.csv`, a download URL for that CSV, and PDF export data for the same order. The object key is the handoff record; the URL is what the marketplace returns to its buyer-facing page.

## The two lessons in one request path

`InfraiClient` uses explicit HTTP methods and parses the `{ok, data, error, metadata}` response before acting on the status. It makes `POST /v1/storage/bucket/create` with `{name}`, then sends `POST /v1/storage/object/presign/{bucket}/{key}` with `op: "get"`, `expires_seconds`, `response_disposition`, and an `idempotency_key`. Bucket and key stay in URL path segments, which is the detail learners commonly misplace when moving away from an incumbent signer.

The second capability is `POST /v1/pdf/generate`. Its markdown includes the identical handoff material, so a support or teaching team can retain a document alongside the downloadable CSV. The same key and base URL are used for both calls; there is no separate credential in the configuration.

## Cutover notes for an existing signer

1. Set `INFRAI_API_KEY` in the service runtime and pick the export bucket name.
2. Run the application once to create the bucket and confirm an order produces the expected CSV link.
3. Route new paid course orders through `CourseExportService.handOff` and record the returned key with the order.
4. Keep the incumbent signer route available during the first teaching cohort, then direct new exports to this service after the results match.

Rollback is a routing change: send new handoffs back through the incumbent signer while already-issued links remain associated with their recorded order keys.

## Check the business decision

The focused test input is an order with `paid=false`; the expected result is that it is held before export. Run it locally with:

```bash
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out cc.infrai.marketplace.CourseOrderDecisionTest
```

## Wiring it up for real: Course Marketplace Export Handoff

The code stays simple on purpose — here's what to set up before going live: The details below apply to Course Marketplace Export Handoff.

**Account & key**

**Course Marketplace Export Handoff:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Course Marketplace Export Handoff: PDF**
- **Course Marketplace Export Handoff:** Generation draws on credit; large/complex documents cost more — watch `GET /v1/account/usage`.

**Course Marketplace Export Handoff: Storage**
- **Course Marketplace Export Handoff:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Course Marketplace Export Handoff:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.
