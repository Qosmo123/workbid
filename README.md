# Workbid for Android

Homeowners post jobs. Contractors bid with a price, days of work, the date they can be there and an arrival window. Accepting a bid books that date. Booked jobs carry work orders (line items + 5% GST) that the homeowner approves, receipts, and a status from Scheduled → In progress → Complete.

Kotlin · Jetpack Compose · Material 3 · Firebase Auth (email/password) · Cloud Firestore (live updates). Targets Android 16 (API 36).

> **New owner? Start with [`HANDOFF-GUIDE.pdf`](HANDOFF-GUIDE.pdf).** It covers setup, the Google Play launch step by step, getting customers, and running the app, written for a complete beginner. Its source is `docs/handoff-guide.html`.

## 1. Set up Firebase (about 10 minutes, free tier)

1. Go to https://console.firebase.google.com → **Add project** → name it `Workbid`. Analytics is optional.
2. **Add app → Android**. Package name: `app.workbid`. Register, then **download `google-services.json`** and put it in the `app/` folder of this project (next to `app/build.gradle.kts`).
3. **Build → Authentication → Get started → Sign-in method → Email/Password → Enable.**
4. **Build → Firestore Database → Create database.** Pick a location near you (e.g. `northamerica-northeast1` Montréal), start in **production mode**.
5. In Firestore, open the **Rules** tab, replace everything with the contents of `firestore.rules` from this project, and **Publish**.

## 2. Build and run

1. Install Android Studio from https://developer.android.com/studio.
2. **File → Open** and pick this `Workbid` folder. Let Gradle sync.
3. Plug in your phone with USB debugging on, or start an emulator.
4. Press **Run ▶**. This connects to your **live** Firebase project.

Command-line builds need JDK 17 or newer (Android Studio's bundled JDK works). A Java *runtime* (JRE) can't compile.

### Practise with demo data (no live data touched)

```
cd rules-test
npm install
npm run emulators          # leave running
npm run seed               # in a second terminal: demo accounts, jobs and bids
cd ..
gradlew installDebug -Pworkbid.emulator=true
```

Sign in as `hana@example.com` (homeowner) or `pat@example.com` (contractor), password `workbid-demo`. Debug builds made with `-Pworkbid.emulator=true` talk to the local emulators at `10.0.2.2`. Release builds never do.

### Release builds

Before the first Play upload, fill in `app/src/main/java/app/workbid/AppConfig.kt` (support email, privacy policy, account deletion and terms URLs).

Signed bundles use `keystore.properties` in the project root (git-ignored):

```
storeFile=C:/path/to/workbid-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Then run `gradlew bundleRelease`. Release builds are minified with R8 (about 3 MB). Raise `versionCode` in `app/build.gradle.kts` for every upload. The full walkthrough is in chapter 5 of the guide.

Play Store images are in `store-assets/`. Regenerate them with `python store-assets/make_assets.py shots docs/images`.

## How it works

| Who | Can do |
|---|---|
| Homeowner | Post a job, compare bids, accept one, change the booking, approve work orders, add receipts, mark complete, delete an open job, report bids |
| Contractor | Browse and search open jobs, bid / edit / withdraw, see their schedule, change the booking, start work, add work orders and receipts, mark complete, report jobs |
| Everyone | Sign up, reset password, verify email, edit name, delete account (Account screen) |

Everyone signs in with email. The Homeowner / Contractor switch at the top changes what you see, so one account can do both.

Data lives in Firestore collections `jobs`, `bids`, `workorders`, `receipts`, `users` and `reports`. The rules in `firestore.rules` enforce who can do what:

- **Jobs** move only forward: open → scheduled → in progress → complete. Accepting a bid must copy that bid's price, contractor, date and window exactly. After booking, either side can move the date or window, only the contractor can start work, either can mark it complete, and nobody can change the price. Only open jobs can be deleted.
- **Bids**: one per contractor per job (stored at `bids/{jobId}_{contractorId}`), never on your own job, editable only while the job is open. The booked bid can't be withdrawn.
- **Work orders** come only from the booked contractor and start unapproved. The homeowner approves each one once.
- **Receipts** come from either party, while the job is booked and not yet complete.
- **Blocking**: each user's block list lives at `users/{uid}/blocked/{otherUid}` and only they can read it. A blocked contractor can't create or edit bids on that homeowner's jobs. The app also hides blocked people's jobs and bids.
- **Sales tax**: each bid carries `taxRate` (0, 0.05, 0.13, 0.14 or 0.15, matching `TAXES` in `ui/Components.kt`). Accepting a bid copies it onto the job, and work-order totals use it.
- **Contractor profiles** (`profiles/{uid}`) are readable by anyone signed in and writable only by their owner. Trades must come from the app's list. Everything in them is self-reported.
- **Messages** can only be sent once a job is booked, only between its homeowner and contractor (`parties`), and can't be edited or deleted.
- **Reports** can be created by anyone signed in. Only admins can read them, mark them reviewed, or remove the reported open job or bid (Account → Owner tools → Review reports). An admin is anyone with a document at `admins/{uid}`, which can only be created in the Firebase console.
- **Who can read what**: open jobs are visible to everyone signed in. Booked and finished jobs are visible to their homeowner, the booked contractor, and contractors who bid on them. Bids are sealed: only the bidder and the homeowner (`jobOwner`) can read them. Work orders and receipts are readable only by the two people on the job (`parties`). The app never downloads whole collections; each query in `data/Repo.kt` matches one of these read rules, so a new query must fit a rule or Firestore refuses it.
- **Every write is validated**: field types, lengths, positive amounts, dates as `yyyy-MM-dd`, and trades, windows, timings and report reasons from the app's lists. Names on jobs and bids must match the poster's `users/{uid}` profile.

**Whenever `firestore.rules` changes, paste it into Firestore → Rules again and Publish.** The lists in `ui/Components.kt` (`CATS`, `WINDOWS`, `TIMING`, `REPORT_REASONS`, and the rates in `TAXES`) must match the lists in the rules exactly.

### Testing the rules
`rules-test/` runs the rules against the Firestore emulator (43 tests). It needs Node.js and Java 21 or newer.

```
cd rules-test
npm install
npm test
```

## Project layout

```
app/src/main/java/app/workbid/
  AppConfig.kt          support email and web links shown in the app
  MainActivity.kt       entry point; connects to emulators in emulator debug builds
  data/Models.kt        data classes + Firestore mapping
  data/Repo.kt          live listeners and writes
  ui/AppViewModel.kt    sign-in, state, all actions
  ui/Screens.kt         sign-in, tabs, job lists, schedule
  ui/Detail.kt          job detail, bid / work order / receipt forms, reporting
  ui/Account.kt         profile, help, sign out, delete account
  ui/Components.kt      cards, fields, pickers, formatting, shared lists
  ui/Theme.kt           colours, type and shapes (light + dark)
app/src/debug/          allows plain HTTP to the local emulators (debug only)
rules-test/             rules tests, emulator launcher, demo-data seeder
store-assets/           Play Store icon, feature graphic, screenshots + generator
docs/                   handoff guide source and screenshots
```

## Next steps
See chapter 10 of the guide and `AUDIT.md`. In priority order: restrict reads per user, load only the data each screen needs, push notifications.
