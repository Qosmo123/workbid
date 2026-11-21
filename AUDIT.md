# Workbid audit: is it ready for real-world use?

Date: 2026-11-20 · Scope: all source under `app/`, `firestore.rules`, build config, README. Code was read, not run.

> **Update 2026-11-20: blockers B1–B6 are fixed** on branch `fix/audit-blockers`:
> - The app compiles (`:app:compileDebugKotlin`).
> - 21 rules tests pass in the Firestore emulator (`rules-test/`).
> - **Not yet done:** running the app on a device, and republishing `firestore.rules` in the Firebase console. The High and Medium items below are still open.
> - **Also fixed:** deleting a job is now a single batch (Medium), and the sign-up name is capped at 60 characters to match the rules.

> **Update 2026-11-20 (UI and hand-off): more items addressed** on branch `feature/ui-and-handoff`:
> - **H3** fixed: in-app account deletion (Account → Delete account, with password confirmation). A web deletion page template is in the hand-off guide.
> - **H4** fixed: Forgot password, verification email on sign-up, and resend/refresh on the Account screen.
> - **H5** fixed: release signing via `keystore.properties`, and the README and guide now use signed bundles and Play testing tracks.
> - **H6** fixed: compileSdk/targetSdk 36. Also AGP 8.11.1, Gradle 8.13, Kotlin 2.0.21.
> - **H7** partly fixed: links in `AppConfig.kt`, plus privacy policy and terms templates in the guide. The owner still has to publish them.
> - **Added:** in-app reporting of jobs and bids (`reports` collection, with rules and tests), which Google Play's user-generated content policy requires.
> - **Medium:** the accept dialog no longer promises notifications; work orders and receipts are blocked on completed jobs (since B4–B6).
> - **Low:** git initialised, R8 and resource shrinking on (release ≈ 2.7 MB), versionCode 2.
> - **Still open:** H8 (App Check), money as cents, notifications, Crashlytics. Chapter 10 of `HANDOFF-GUIDE.pdf` has the order.

> **Update 2026-11-20 (read privacy and loading):**
> - **H1** fixed: bids are readable only by the bidder and the job's homeowner (`jobOwner` on each bid). Work orders and receipts only by the two parties (`parties` array). Booked and finished jobs only by their parties and by contractors who bid on them.
> - **H2** fixed: no whole-collection listeners. The app runs scoped queries (`status == open`, `postedBy == me`, `contractorId == me`, `contractorId`/`jobOwner == me` for bids, `parties array-contains me`), plus one-document listeners for jobs a contractor bid on and lost.
> - 28 rules tests pass, including 6 new ones for read privacy and the exact queries the app runs. The queries were also run as the seeded demo users against the emulators.
> - **Deploy note:** this app version needs the new rules, and the old app version can't run under them. Publish `firestore.rules` together with this release. Bids, work orders and receipts written before this change lack the new fields and become invisible. That's fine pre-launch; otherwise backfill them in the console.

> **Update 2026-11-20 (blocking and sales tax):**
> - **Blocking:** Block on a bid or in a job's ⋮ menu; list and unblock under Account. Enforced by the rules (`blockedBy()`), not just hidden in the app.
> - **Sales tax per bid** replaces the fixed 5% GST: GST 5%, HST 13/14/15%, or none. Copied onto the job when accepted; work orders use it.
> - 34 rules tests pass.

> **Update 2026-11-20 (profiles and messages):**
> - **Contractor profiles:** trades, service area, years, licence, insurance and an about section, edited under Account. Homeowners see badges on each bid and the full profile from the bid or booking. Labelled as self-reported.
> - **Messages:** a chat per booked job between the homeowner and the contractor, with unread counts on the job screen. Rules allow only the two parties, only after booking, with no edits or deletes.
> - The Data safety answers and privacy policy template in the guide now include in-app messages and profiles.
> - 39 rules tests pass.

**Verdict (original audit): not ready yet.** The screens and flows are solid for a prototype. Three problems in the data and sign-in layer mean people would think things saved when they didn't, and see data that isn't real. The Firestore rules also let either party rewrite a booked job. Fix the Blockers before anyone outside your own testing uses it.

---

## Blockers: fix before anyone else uses it

### ✅ B1. A wrong password still "signs you in"
`ui/AppViewModel.kt:76-95` and `:102-121`. If Firebase sign-in or sign-up throws for any reason (wrong password, no such account, no network, email already in use), the `catch` creates a fake local user (`user_<hash of email>`) and lets them in. That user has no Firebase session, so every write afterwards is rejected by the rules, and the rejection is hidden (see B2). Line 29 also prefers that saved fake `local_uid` over the real Firebase user on later launches.

**Real-world effect:** someone mistypes their password, gets in anyway, posts a job or a bid, sees "Job posted", and nobody else ever sees it.

**Fix:** remove the local fallback entirely. Show the Firebase error (wrong password, account exists, offline) and stay on the sign-in screen. Take `uid` only from `auth.currentUser` (or an `AuthStateListener`).

### ✅ B2. Failed saves are reported as success
- `data/Repo.kt:68-95`: `add`, `set`, `update` and `delete` all `catch (_: Exception) { }` and then write a copy into local memory anyway.
- `ui/AppViewModel.kt:170-173`: `act()` shows the *success* message for every error that isn't PERMISSION_DENIED (`else ok`).

**Real-world effect:** "Bid sent", "Booked…", "Work order approved" appear even when nothing reached the server. That's data loss users can't see.

**Fix:** let exceptions reach `act()`, show a real error message, and drop the local copy. Firestore already has an offline cache, so writes made while offline queue and sync on their own. No hand-written fallback is needed.

### ✅ B3. The lists show fake and local data instead of the database
`data/Repo.kt:34-62`. Each listener sends Firestore snapshots *and* the in-memory `localFlow` into the same stream. Whenever a local list changes (after every write, see B2), the screen switches to that local list. It holds the hard-coded demo job "Bathroom Vanity…" by "Alex Morgan", a demo bid from "Apex Plumbing & Tile", and only this session's writes. If the listener errors, it falls back to the demo data too.

Local copies also get made-up IDs (`local_…`, `Repo.kt:69`). Edit or withdraw a bid you just sent and the code calls `update`/`delete` on `bids/local_…`, a document that doesn't exist in Firestore. The error is swallowed, so the real bid never changes.

**Real-world effect:** real users see a fake job and a fake bid, lists go stale after the first action, and edits or withdrawals silently do nothing.

**Fix:** delete the local fallback (`localJobs`…`localReceipts`, the `mapTo*`/`applyDataTo*` helpers, the demo data) and have `listen()` send only Firestore snapshots. Pass errors on through `close(err)` so the existing `catch` in the view model shows them.

### ✅ B4. Firestore rules let either side rewrite a booked job
`firestore.rules:20-22`. Once a job is booked, the homeowner **or** the contractor can update any field except `postedBy`. That means:
- the contractor can change `price`, mark the job complete, or set `contractorId` to someone else;
- the homeowner can change the agreed price after booking, or "accept" a made-up contractor ID with no bid behind it, because nothing checks that `acceptedBidId` is a real bid on this job;
- either can set `status` to anything, in any order (complete → open, and so on).

Anyone with the app's public API key can do this from a script, not just from the app.

**Fix:** split updates by role and state. For example:
- **Homeowner, `open` → `scheduled`:** only the booking fields change, `acceptedBidId` must point to a bid on this job (check it with `get()`), and `contractorId`/`price` must match that bid.
- **Contractor:** may change only `status` (scheduled → in_progress → complete) plus `completedAt`, `scheduledDate` and `window`.
- **Homeowner after booking:** may change only `scheduledDate`, `window`, and complete.
- **Delete:** only while `status == 'open'`.

### ✅ B5. Bid rules are too loose
`firestore.rules:28-34`.
- A homeowner can bid on their own job. Add `job(...).postedBy != me()`.
- A contractor can place any number of bids on one job. Use a fixed ID such as `bids/{jobId}_{uid}` so each contractor gets one.
- A contractor can still edit `amount` after being booked. Allow updates only while the job is `open`, and keep `jobId` unchanged.

### ✅ B6. No checks on what gets written
No rule checks field types, sizes or ranges. Anyone with the API key can write negative prices, 1 MB descriptions, `approved: true` on a new work order, or any `postedByName` (for example "Workbid Support"). Add `is string`/`is number`, `size()` limits, `> 0` checks and `keys().hasOnly([...])` to every create and update. `users/{id}` (`:13`) also accepts any fields at all.

---

## High: needed before real customers or Play Store

| # | Issue | Where | Why it matters |
|---|---|---|---|
| H1 | **Every signed-in user can read every job, bid, work order and receipt** | `firestore.rules` (all `allow read: if signedIn()`) | Contractors can see competitors' bid prices. Receipts and work orders (people's spending) are readable by anyone with an account. That's a privacy problem under Canadian privacy law (PIPEDA) and a trust problem for sealed bidding. |
| H2 | **The app downloads entire collections** | `Repo.kt:43` (`collection(x).addSnapshotListener` with no query) | Every launch reads every document in all four collections. At 5,000 jobs and 20,000 bids, one app open costs about 25k billable reads, and it gets slower as data grows. Query instead: `status == open` for browsing, `postedBy == uid` / `contractorId == uid` for "mine", and bids/work orders/receipts `where jobId == X` only when a job is open on screen. The rules then need to match those queries. |
| H3 | **No account deletion** | — | Google Play requires apps that create accounts to offer account and data deletion in the app and on the web. |
| H4 | **No password reset or email verification** | `ui/Screens.kt` sign-in | People who forget their password are locked out. Unverified emails allow throwaway accounts. |
| H5 | **Sharing the debug APK** | README "Make an APK" | Debug builds are debuggable and signed with your machine's debug key. You can't ship updates over them from another machine. Use a signed release build, or Play internal testing. |
| H6 | **targetSdk 34** | `app/build.gradle.kts:16` | Below Play's current minimum for new apps and updates. Raise compileSdk/targetSdk to the level Play currently requires. |
| H7 | **Privacy policy** | — | Required by Play for any app with accounts. Also needed for PIPEDA. |
| H8 | **No Firebase App Check** | — | Without it, anyone can call your Firestore using the API key in the APK. App Check plus tight rules (B4–B6) closes that. |

## Medium: correctness and product gaps

- **GST hard-coded at 5%** (`ui/Components.kt:44`, work order totals in `Detail.kt:347-348`, bids shown "+ GST"). That's only right in AB, the territories, and alongside PST in BC/SK/MB/QC (where PST isn't added). ON is 13% HST, and the Atlantic provinces are 14–15%. Contractors earning under $30k a year often aren't GST-registered and shouldn't charge it. Make the tax rate a per-contractor or per-province setting.
- **Money stored as `Double`** (`Models.kt`). Rounding errors build up across line items and totals. Store integer cents.
- **Job references can collide**: `"WB-" + now % 100000` (`AppViewModel.kt:187`) repeats every 100 seconds' worth of milliseconds. Use the document ID, or a counter in a transaction.
- **Work order numbers are computed on the device** (`AppViewModel.kt:267`), so two quick submissions get the same number. Rules also allow work orders and receipts on completed jobs.
- **Deleting a job isn't atomic** (`AppViewModel.kt:298-303`). Bids are deleted one by one, then the job, with no transaction. A failure partway leaves orphan bids. Use a batched write. The rules also allow deleting booked jobs, which orphans work orders and receipts.
- **The accept dialog promises something that doesn't happen**: it says "Other bids will be marked not selected" (`Detail.kt:202`), but nothing tells the losing bidders.
- **No notifications.** On a bidding marketplace, people won't know a bid or booking happened unless they open the app. FCM (Firebase Cloud Messaging) plus a Cloud Function is the usual minimum.
- **Names can be faked**: `postedByName` and `contractorName` are copied from the device, not from a verified profile.
- **Contractor trust**: there are no licence, insurance, WorkSafeBC or reviews fields. For real homeowners that's a liability and adoption issue, not just a missing feature.

## Low: housekeeping

- **Not a git repository.** Run `git init` and commit before changing anything.
- **Dependencies are about two years old**: AGP 8.5.2, Kotlin 2.0.20, Compose BoM 2024.09, Firebase BoM 33.3.0. Update them together.
- **R8/minify is off for release** (`app/build.gradle.kts:21`). Turn it on for a smaller APK. The manual Firestore mapping means no keep rules are needed.
- **`allowBackup="true"`** backs up the SharedPreferences, including the fake `local_uid` from B1. Set data extraction rules or turn backup off.
- **No crash reporting** (add Crashlytics), **no tests**, and `versionCode` is stuck at 1.
- `google-services.json` is present and git-ignored. That's fine: it isn't a secret, but restrict the API key in Google Cloud Console to the Android app's package and SHA-1.

---

## Suggested order

1. `git init` and commit what's there.
2. B1–B3: remove the local/demo fallback from `Repo.kt` and `AppViewModel.kt` and show real errors. (Mostly deleting code.)
3. B4–B6 + H1: rewrite `firestore.rules` with role/state checks and field validation. Test them in the Firestore emulator.
4. H2: replace the whole-collection listeners with scoped queries.
5. H3–H8: account deletion, password reset, privacy policy, App Check, targetSdk, signed release.
6. Then GST/HST handling, integer cents, notifications.
