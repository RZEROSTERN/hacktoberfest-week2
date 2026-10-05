package mx.dev1.naturequest

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import mx.dev1.naturequest.domain.verification.PhotoStore
import javax.inject.Inject

@HiltAndroidApp
class NatureQuestApp : Application() {
    @Inject
    lateinit var photoStore: PhotoStore

    override fun onCreate() {
        super.onCreate()
        // A hunt only lives in memory, so photos left over from a previous run (a crash, a kill)
        // belong to a hunt that no longer exists. Delete them.
        photoStore.clear()
    }
}
