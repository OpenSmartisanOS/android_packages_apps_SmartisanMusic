# NetEase API module

This Android library is the isolated NetEase network foundation for Smartisan Music. The app
creates a client only after the user explicitly enables Online Mode. The current UI uses it for
account validation and read-only account playlists and tracks through the app's existing playlist
pages; playback and the rest of the library UI remain local-only.

```kotlin
val client = NeteaseApiClient.create(applicationContext)

when (val result = client.api.searchSongs("海阔天空")) {
    is NeteaseResult.Success -> useSongs(result.value.items)
    is NeteaseResult.Failure -> showError(result.error)
}

// A future explicit login flow may import browser cookies. They are encrypted at rest.
client.sessionManager.importCookieHeader("MUSIC_U=...; __csrf=...")
```

The client supports search, song detail, lyrics, album/artist/user data, playlists,
recommendations, new releases, personal FM, and EAPI stream URL resolution. It accepts only
fixed NetEase API paths and origins. It never logs Cookies, decrypted EAPI parameters, response
bodies, or resolved stream URLs.

Call `close()` when the owning application component is permanently disposed. Use a single
long-lived client for normal application integration so Cookie updates share one in-memory view.
