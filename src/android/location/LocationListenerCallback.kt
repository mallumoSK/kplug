package tk.mallumo.cordova.kplug.location

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.os.Bundle

class LocationListenerCallback(
    val context: Context,
    val onNewLocationEntry: (newLocationEntry: LocationResponse) -> Unit
) : LocationListener {

    override fun onLocationChanged(location: Location?) {
        if (location != null) {
            onNewLocationEntry(
                location.ofLocationResponse(
                    context,
                    LocationResponse.State.NEW_LOCATION
                )
            )
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    override fun onProviderEnabled(provider: String?) {
        if (provider != null) {
            onNewLocationEntry(
                LocationResponse(
                    state = LocationResponse.State.PROVIDER_ENABLED,
                    provider = provider
                )
            )
        }
    }

    override fun onProviderDisabled(provider: String?) {
        if (provider != null) {
            onNewLocationEntry(
                LocationResponse(
                    state = LocationResponse.State.PROVIDER_DISABLED,
                    provider = provider
                )
            )
        }
    }
}