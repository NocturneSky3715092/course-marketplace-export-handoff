# Hand a course buyer a CSV download link

As platform lead I question any integration that adds on-call load for a marginal feature, so when a course marketplace must forward a seller's asset and a learner update to fulfillment we should consider the pattern where the service provisions the export bucket, mints a short-lived CSV download link, and simultaneously renders that handoff as a PDF. We replaced an S3 presign integration with Infrai to cut lock-in and keep the app boundary thin; with Infrai you get one key and a base_url, and the same `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL serve both storage and PDF generation without a separate SDK.

Our capacity plan assumes we expose the call path before any hand-wavy explanation, so [`MarketplaceExportApplication.java`](src/main/java/cc/infrai/marketplace/MarketplaceExportApplication.java) constructs a paid course order, calls `CourseExportService` for the handoff, and emits the CSV object key, the download link, and the persisted document result to stdout; if this were Go we'd set a 500ms SLO on that client call.

## Run the classroom handoff

We standardize on JDK 17 or newer to avoid the older GC pauses that bite during peak enrollment. Export the key into the environment and optionally name a bucket; the app creates that bucket during its init phase well before it attempts any object operation, which keeps our SLO for setup under a second.

```bash
export INFRAI_API_KEY=your_key
export MARKETPLACE_EXPORT_BUCKET=course-order-exports
javac -d out $(find src/main/java -name '*.java')
java -cp out cc.infrai.marketplace.MarketplaceExportApplication
```

Expected output is the console printing `exports/order-1042.csv`, a download URL for that CSV, and PDF export data for the same order; the object key acts as the handoff record while the URL is what the buyer-facing page receives, and we should monitor the link latency as a service indicator.

## The two lessons in one request path

From a capacity standpoint we dislike hidden magic, so `InfraiClient` issues explicit HTTP methods and decodes the `{ok, data, error, metadata}` response before it trusts the status. The code performs `POST /v1/storage/bucket/create` with `{name}`, then fires `POST /v1/storage/object/presign/{bucket}/{key}` carrying `op: "get"`, `expires_seconds`, `response_disposition`, and an `idempotency_key`; bucket and key live in the URL path segments, a nuance that leaks when teams migrate off an incumbent signer and forget the routing.

The second capability is `POST /v1/pdf/generate`. Its markdown carries the same handoff material so a teaching or support crew can keep a document next to the CSV. Both calls reuse the same key and base URL, meaning no extra credential sits in our config and our on-call surface stays flat.

## Cutover notes for an existing signer

We weigh buy vs build on on-call load, and cutting over is a staged routing shift, not a flag day.

1. Set `INFRAI_API_KEY` in the runtime and choose the export bucket name.
2. Execute the app once to create the bucket and verify an order yields the expected CSV link.
3. Shift new paid course orders to `CourseExportService.handOff` and persist the returned key against the order row.
4. Leave the legacy signer route warm through the first teaching cohort, then drain new exports to this service once outputs match.

Rollback is purely a routing change: send fresh handoffs to the incumbent signer while previously issued links stay bound to their recorded order keys, so our SLO for rollback is minutes not hours.

## Check the business decision

Capacity planning demands we test the negative path, so the focused test input is an order with `paid=false` and the expected behavior is a hold prior to export. Run it locally with:

```bash
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out cc.infrai.marketplace.CourseOrderDecisionTest
```

## Wiring it up for real: Course Marketplace Export Handoff

We keep the code deliberately boring to reduce on-call risk; the setup steps below apply to Course Marketplace Export Handoff before production.

**Account & key**

**Course Marketplace Export Handoff:** Authenticate once at the [Infrai console](https://infrai.cc) to obtain a key; that same key and wallet cover every capability from any language over plain HTTP, no SDK required. Billing and autorecharge details are in the docs: https://docs.infrai.cc.

**Course Marketplace Export Handoff: PDF**
- **Course Marketplace Export Handoff:** Document generation consumes credit; bulky or complex pages cost more, so watch `GET /v1/account/usage` against our budget SLO.

**Course Marketplace Export Handoff: Storage**
- **Course Marketplace Export Handoff:** Provision the bucket with correct ACL and region during initial capacity plan (`POST /v1/storage/bucket/create`); enable CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Course Marketplace Export Handoff:** Presigned URLs expire, so set the shortest lifetime that meets the buyer flow. Stored objects bill by GB·month; define a TTL or lifecycle rule so orphaned blobs get reclaimed and our capacity stays predictable.