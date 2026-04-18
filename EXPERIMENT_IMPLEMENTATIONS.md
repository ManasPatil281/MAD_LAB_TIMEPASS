# Experiment Implementations in Project (MAD_LAB_TIMEPASS)

This document provides a summary of the 8 experiments implemented in the **ScholarMate** (MAD_LAB_TIMEPASS) project, explaining which features of the code correspond to each experiment.

---

### 1. Install and Configure Android Studio and "Hello World"
- **Implementation**: The entire project structure is built using the **Android Studio** development environment.
- **Reference**: 
  - `build.gradle` defines the environment and dependencies.
  - `SplashActivity.java` serves as the initial entry point, displaying a branding logo similar to a "Hello World" start.
- **Key Location**: `app/src/main/java/com/example/myapplication/SplashActivity.java`

### 2. Design a Responsive UI using LinearLayout & ConstraintLayout
- **Implementation**: The project uses **ConstraintLayout** for complex views (like the Home Screen) and **LinearLayout/CardView** for organized inputs and buttons.
- **Reference**:
  - `activity_home.xml`: Uses `ConstraintLayout` to align various feature cards.
  - `activity_login.xml`: Uses `LinearLayout` elements inside a `CardView` for a clean, responsive login form.
- **Key Location**: `app/src/main/res/layout/activity_home.xml`

### 3. Multiple Screens using Intents and Fragments
- **Implementation**: Navigation between features is handled via **Intents**, and summarized results or pop-ups are handled via **Fragments**.
- **Reference**:
  - `HomeActivity.java`: Uses `Intent` to launch features like `PaperReviewActivity`, `GrammarCheckActivity`, etc.
  - `ResultBottomSheetFragment.java`: An implementation of `DialogFragment` (Fragment) used to display AI results as a slide-up panel.
- **Key Location**: `app/src/main/java/com/example/myapplication/HomeActivity.java`

### 4. Event-Driven Interactions with Buttons and Gestures
- **Implementation**: Handled using `OnClickListener` for buttons and a dedicated **GesturePlayground** for advanced interactions.
- **Reference**:
  - `GesturePlaygroundActivity.java`: Implements `GestureDetector` and `ScaleGestureDetector` to handle:
    - **Single Tap / Double Tap**
    - **Long Press**
    - **Fling / Swipe**
    - **Pinch Zoom (Scale)**
    - **Two-finger Rotation**
- **Key Location**: `app/src/main/java/com/example/myapplication/GesturePlaygroundActivity.java`

### 5. Local Data Persistence using SharedPreferences
- **Implementation**: The app saves the user's login session and JWT token locally to prevent re-logging every time the app opens.
- **Reference**:
  - `SessionManager.java`: Uses `SharedPreferences` with methods like `saveToken()`, `getToken()`, and `clearSession()`.
- **Key Location**: `app/src/main/java/com/example/myapplication/SessionManager.java`

### 6. Fetching and Displaying Data from a REST API
- **Implementation**: The app communicates with a **FastAPI backend** using the `OkHttp` library and `Gson` for JSON parsing.
- **Reference**:
  - `ApiClient.java`: Centralized class for all network requests (`POST`, `GET`) using `MultipartBody` for files and `JSONObject` for raw strings.
- **Key Location**: `app/src/main/java/com/example/myapplication/ApiClient.java`

### 7. Integrates a device sensor (Accelerometer) and Media Handling (Images)
- **Implementation**: The application uses the **Accelerometer** sensor to detect a custom shake gesture and uses the Android **Media Picker** API to allow the user to select and preview a profile picture from their gallery.
- **Reference**:
  - `HomeActivity.java`: Implements `SensorEventListener` measuring geometric changes via `Sensor.TYPE_ACCELEROMETER` values to parse shakes. It also initializes `registerForActivityResult()` pointing to `ActivityResultContracts.GetContent()` restricting MIME to `image/*` to retrieve the content URI.
- **Key Location**: `app/src/main/java/com/example/myapplication/HomeActivity.java`

### 8. Implement Notifications and Background tasks using Android Services
- **Implementation**: An Android `Service` creates a customized notification channel alerting the user in the background. It evaluates runtime permissions and is triggered globally asynchronously via standard intent mechanisms.
- **Reference**:
  - `SensorNotificationService.java`: Exerts `Service` lifecycles and routes a system `NotificationCompat.Builder` explicitly in `onStartCommand()`. 
  - `AndroidManifest.xml`: Documents the `<service>` node block along with the API 33 `POST_NOTIFICATIONS` requisite constraint.
- **Key Location**: `app/src/main/java/com/example/myapplication/SensorNotificationService.java`
