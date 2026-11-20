# Workbid for Android

Homeowners post jobs. Contractors bid with a price, days of work, the date they can be there and an arrival window. Accepting a bid books that date. Booked jobs carry work orders (line items + 5% GST) that the homeowner approves, receipts, and a status from Scheduled → In progress → Complete.

Kotlin · Jetpack Compose · Material 3 · Firebase Auth (email/password) · Cloud Firestore (live updates).

## 1. Set up Firebase (about 10 minutes, free tier)

1. Go to https://console.firebase.google.com → **Add project** → name it `Workbid`. Analytics is optional.
2. **Add app → Android**. Package name: `app.workbid`. Register, then **download `google-services.json`** and put it in the `app/` folder of this project (next to `app/build.gradle.kts`).
3. **Build → Authentication → Get started → Sign-in method → Email/Password → Enable.**
4. **Build → Firestore Database → Create database.** Pick a location near you (e.g. `northamerica-northeast1` Montréal), start in **production mode**.
5. In Firestore, open the **Rules** tab, replace everything with the contents of `firestore.rules` from this project, and **Publish**.

## 2. Build and run

1. Install Android Studio (Koala or newer) from https://developer.android.com/studio.
2. **File → Open** and pick this `Workbid` folder. Let Gradle sync (first sync downloads a few hundred MB).
3. Plug in your phone with USB debugging on (Settings → About phone → tap Build number 7 times → Developer options → USB debugging), or start an emulator.
4. Press **Run ▶**.

### Make an APK to share with others
**Build → Build App Bundle(s) / APK(s) → Build APK(s).** Android Studio shows a link to `app-debug.apk`. Send that file to homeowners and contractors; they tap it to install (they'll need to allow "Install unknown apps").

For the Google Play Store, use **Build → Generate Signed App Bundle** and follow Play Console's steps.

## How it works

| Who | Can do |
|---|---|
| Homeowner | Post a job, compare bids, accept one, change the booking, approve work orders, add receipts, mark complete, delete an open job |
| Contractor | Browse open jobs by trade, bid / edit / withdraw, see their schedule, change the booking, start work, add work orders and receipts, mark complete |

Everyone signs in with email. The Homeowner / Contractor switch at the top changes what you see, so one account can do both.

Data lives in Firestore collections `jobs`, `bids`, `workorders`, `receipts` and `users`. The rules in `firestore.rules` make sure people can only change their own jobs and bids, only the booked contractor writes work orders, and the homeowner can only approve them.

## Project layout

```
app/src/main/java/app/workbid/
  MainActivity.kt
  data/Models.kt        data classes + Firestore mapping
  data/Repo.kt          live listeners and writes
  ui/AppViewModel.kt    sign-in, state, all actions
  ui/Screens.kt         sign-in, tabs, job lists, schedule
  ui/Detail.kt          job detail, bid / work order / receipt forms
  ui/Components.kt      cards, pills, pickers, formatting
  ui/Theme.kt           colours (light + dark)
```

## Good next steps
- Photo receipts (Firebase Storage)
- Push notifications for new bids and bookings (Firebase Cloud Messaging)
- Filtering jobs by distance
- Contractor profiles with licence and insurance details
