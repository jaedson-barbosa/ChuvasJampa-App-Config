package com.example.estacao

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.Button
import android.widget.Switch
import androidx.core.app.ActivityCompat
import androidx.navigation.findNavController
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject

class RegisterStation : Fragment(R.layout.fragment_register_station), View.OnClickListener, LocationListener {
    private lateinit var locationManager: LocationManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!GPSAvailable || !StationGPSAvailable) {
            val ctrUseStationGPS = requireView().findViewById<Switch>(R.id.ctrUseStationGPS)
            ctrUseStationGPS.visibility =  View.GONE
        }

        currentStation = StationDB()
        val ctrSave : Button = view.findViewById(R.id.ctrContinue)
        ctrSave.setOnClickListener(this)
    }

    override fun onClick(v0: View?) {
        val ctrName : TextInputEditText = requireView().findViewById(R.id.ctrName)
        val ctrCityCode : TextInputEditText = requireView().findViewById(R.id.ctrCityCode)
        val ctrHomologation : Switch = requireView().findViewById(R.id.ctrHomologation)
        currentStation.name = ctrName.text.toString()
        currentStation.cityCode = ctrCityCode.text.toString().toInt()
        currentStation.homologation = ctrHomologation.isChecked

        val ctrUseStationGPS = requireView().findViewById<Switch>(R.id.ctrUseStationGPS)
        if (ctrUseStationGPS.isChecked || !GPSAvailable) {
            CurrentCallback = this::onGetGPS
            SendToDevice("{\"metodo\":\"GetGPS\"}")
        } else {
            locationManager =
                requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gpsPermission = ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (gpsPermission == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    1f,
                    this
                )
            } else {
                Snackbar.make(
                    requireView(),
                    getString(R.string.deniedPermissions),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun onGetGPS(message: String) {
        val json = JSONObject(message)
        val valid = json["valid"] as Boolean
        if (valid) {
            currentStation.latitude = json["lat"] as Double
            currentStation.longitude = json["lon"] as Double
            save()
        } else Snackbar.make(
            requireView(),
            getString(R.string.gpsNoSignal),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun save() {
        val requestBody = JSONObject()
        requestBody.put("Nome", currentStation.name)
        requestBody.put("CodIBGE", currentStation.cityCode)
        requestBody.put("Latitude", currentStation.latitude)
        requestBody.put("Longitude", currentStation.longitude)
        requestBody.put("Homologacao", currentStation.homologation)

        "https://us-central1-chuvasjampa.cloudfunctions.net/CadastrarEstacao"//"http://192.168.0.109:5001/chuvasjampa/us-central1/CadastrarEstacao"
            .httpPost()
            .jsonBody(requestBody.toString())
            .responseString() { request, response, result ->
                when (result) {
                    is Result.Failure -> {
                        requireActivity().runOnUiThread {
                            Snackbar.make(
                                requireView(),
                                getString(R.string.errorSave),
                                Snackbar.LENGTH_INDEFINITE
                            ).setAction(getString(R.string.retry)) { save() }.show()
                        }
                    }
                    is Result.Success -> {
                        currentStation.id = result.get()
                        requireActivity().runOnUiThread {
                            requireView()
                                .findNavController()
                                .navigate(R.id.action_registerStation_to_configStation)
                        }
                    }
                }
            }
            .join()
    }

    var isLocationFind = false

    override fun onLocationChanged(p0: Location) {
        if (!isLocationFind && p0.accuracy < 10) {
            isLocationFind = true
            locationManager.removeUpdates(this)
            currentStation.latitude = p0.latitude
            currentStation.longitude = p0.longitude
            save()
        }
    }
}