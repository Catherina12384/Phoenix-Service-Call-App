# Phase 1 Setup Guide — ServiceCallApp

Complete these steps **in order** before building Phase 1.
Estimated time: 30–45 minutes.

---

## Step 1 — Create Firebase Project

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Click **Add project** → Name it `ServiceCallApp`
3. Disable Google Analytics (not needed) → **Create project**

---

## Step 2 — Enable Firebase Authentication

1. In Firebase Console → **Authentication** → **Get started**
2. Under **Sign-in method** → Enable **Email/Password**
3. Click **Save**

---

## Step 3 — Create Firestore Database

1. Firebase Console → **Firestore Database** → **Create database**
2. Select **Start in production mode** (we deploy rules separately)
3. Choose region: `asia-south1` (Mumbai) for best performance in India
4. Click **Enable**

---

## Step 4 — Add Android App to Firebase

1. Firebase Console → **Project Settings** (gear icon) → **Your apps**
2. Click **Add app** → Android icon
3. Package name: `com.servicecall.app`
4. App nickname: `ServiceCallApp`
5. Click **Register app**
6. **Download `google-services.json`**
7. Place it at: `app/google-services.json`  ← this file is required to build

---

## Step 5 — Deploy Firestore Security Rules

```bash
# Install Firebase CLI (one-time)
npm install -g firebase-tools

# Login
firebase login

# In the project root (where firebase.json is)
firebase use --add          # select your project
firebase deploy --only firestore:rules
firebase deploy --only firestore:indexes
```

---

## Step 6 — Create Owner Account

Owner account must be created manually in Firebase Console
(agents are created by owner in Phase 2 settings screen).

1. Firebase Console → **Authentication** → **Users** → **Add user**
2. Enter owner email and password
3. Note the **UID** shown in the users list

4. Firebase Console → **Firestore** → `users` collection → **Add document**
   - Document ID: `<owner UID from above>`
   - Fields:
     ```
     uid:            <owner UID>
     name:           "Owner Name"
     email:          "owner@example.com"
     role:           "owner"
     phone:          "+91XXXXXXXXXX"
     telegramChatId: ""
     isActive:       true
     createdAt:      (click Timestamp, set to now)
     ```

---

## Step 7 — Open in Android Studio

1. Open Android Studio → **Open** → select the `phase1/` folder
2. Wait for Gradle sync to complete (downloads dependencies, ~2 min)
3. Confirm `google-services.json` is in `app/` folder
4. Click **Run** (▶) — select your device or emulator (API 26+)

---

## Step 8 — Test Phase 1

| Test | Expected result |
|------|----------------|
| App launch | Splash screen shows, then routes to Login (first run) |
| Login with owner credentials | Routes to MainActivity with admin icon visible in toolbar |
| Login with wrong password | Error Snackbar: "Incorrect password. Please try again." |
| Login with agent account | Routes to MainActivity WITHOUT admin icon |
| Profile tab | Shows name, email, role chip |
| Logout | Returns to LoginActivity, session cleared |
| Kill app, reopen | Splash routes directly to Dashboard (persistent session) |

---

## File Checklist

```
app/
├── google-services.json          ← YOU MUST ADD THIS (from Firebase)
├── src/main/
│   ├── AndroidManifest.xml       ✅
│   ├── java/com/servicecall/app/
│   │   ├── MainActivity.java     ✅
│   │   ├── Fragments.java        ✅ (PlaceholderFragment + ProfileFragment)
│   │   ├── auth/
│   │   │   ├── SplashActivity.java   ✅
│   │   │   ├── LoginActivity.java    ✅
│   │   │   └── AuthViewModel.java    ✅
│   │   ├── models/
│   │   │   ├── User.java         ✅
│   │   │   ├── Task.java         ✅
│   │   │   └── Report.java       ✅
│   │   └── utils/
│   │       ├── SessionManager.java       ✅
│   │       ├── FirestoreRepository.java  ✅
│   │       ├── FCMService.java           ✅
│   │       └── BootReceiver.java         ✅
│   └── res/
│       ├── layout/
│       │   ├── activity_splash.xml   ✅
│       │   ├── activity_login.xml    ✅
│       │   ├── activity_main.xml     ✅
│       │   ├── fragment_placeholder.xml ✅
│       │   └── fragment_profile.xml  ✅
│       ├── drawable/
│       │   ├── ic_dashboard.xml  ✅
│       │   ├── ic_tasks.xml      ✅
│       │   ├── ic_calls.xml      ✅
│       │   ├── ic_ai.xml         ✅
│       │   ├── ic_profile.xml    ✅
│       │   ├── ic_add.xml        ✅
│       │   ├── ic_admin.xml      ✅
│       │   ├── ic_notification.xml ✅
│       │   ├── ic_email.xml      ✅
│       │   ├── ic_lock.xml       ✅
│       │   ├── ic_splash_logo.xml ✅
│       │   └── nav_selector.xml  ✅
│       ├── menu/
│       │   └── bottom_nav_menu.xml ✅
│       └── values/
│           ├── colors.xml        ✅
│           ├── strings.xml       ✅
│           └── themes.xml        ✅
build.gradle (project)            ✅
app/build.gradle                  ✅
settings.gradle                   ✅
firestore.rules                   ✅
firestore.indexes.json            ✅
```

---

## What's Next — Phase 2

Phase 2 will build:
- `CreateTaskActivity` — log a new task (customer name, phone, type, description)
- `TaskListFragment` — the office tasks shared pool with swipe gestures
- `TaskDetailActivity` — full task view with Mark Done and Snooze buttons
- `TaskAdapter.java` — RecyclerView adapter with colour-coded status chips
- `TaskViewModel.java` — LiveData-backed task state management
- `DashboardFragment` — today's summary cards (pending / done / overdue counts)
- Firestore read/write for tasks
