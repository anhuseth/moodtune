# MoodTune

Mood based music. Tell MoodTune how you feel, or let it read the day, and it opens a Spotify playlist that fits.

The Android app lives in `android/`. It mirrors the MoodTune flow: a mood reading from health and chat, then playback in Spotify.

## Android

Open the `android` directory in Android Studio, or build from the command line:

```bash
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Health signals come from Health Connect: heart rate, heart-rate variability, sleep, steps, active energy, workouts, and mindfulness. When Health Connect has no samples, the app can show sample figures so the mood screen still works.

Spotify sign-in uses PKCE. In the Spotify developer dashboard, add this redirect URI:

```text
moodtune://callback
```

Paste the Client ID into Settings in the app. Remote playback needs Spotify Premium and an active device. Without that, MoodTune opens the playlist in the Spotify app or on the web.
