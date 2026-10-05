package mx.dev1.naturequest.data.network

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.dev1.naturequest.domain.model.NetworkInfo
import javax.inject.Inject

class ConnectivityNetworkInfo @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkInfo {
    override fun isMetered(): Boolean =
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
}
