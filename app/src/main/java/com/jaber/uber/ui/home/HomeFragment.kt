package com.jaber.uber.ui.home

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.jaber.uber.Common
import com.jaber.uber.R
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import java.io.IOException
import java.util.*

class HomeFragment : Fragment(), OnMapReadyCallback {

    var currentUserUid = FirebaseAuth.getInstance().currentUser?.uid.toString()

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment :SupportMapFragment

    //Location System Variables
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    //Online System Variables
    private lateinit var onlineRef:DatabaseReference
    private  var currentUserRef:DatabaseReference? = null
    private lateinit var driverLocationRef:DatabaseReference
    private lateinit var geoFire:GeoFire
    private val onlineValueEventListener = object:ValueEventListener{

        override fun onDataChange(snapshot: DataSnapshot) {
            if(snapshot.exists() && currentUserRef != null)
                currentUserRef!!.onDisconnect().removeValue()
        }

        override fun onCancelled(error: DatabaseError) {
            Snackbar.make(requireView(), error.message,Snackbar.LENGTH_LONG).show()
        }

    }

    override fun onDestroy() {
        //Remove Current Location System when shut down
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)

        //Remove Online System when shut down
        geoFire.removeLocation(currentUserUid)
        onlineRef.removeEventListener(onlineValueEventListener)

        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        registerOnlineSystem()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)

        init()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        return root
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.uiSettings.isZoomControlsEnabled = true

        //Request permission
        Dexter.withContext(context)
            .withPermission(ACCESS_FINE_LOCATION)
            .withListener(object :PermissionListener{
                @SuppressLint("MissingPermission")
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                    //Enable button first
                    mMap.isMyLocationEnabled =true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMyLocationButtonClickListener {
                        Toast.makeText(context, "Location Button Clicked", Toast.LENGTH_LONG).show()
                        fusedLocationProviderClient.lastLocation
                            .addOnFailureListener { ex ->
                                Toast.makeText(context, "Permission Request Error ${ex.message}", Toast.LENGTH_LONG).show()
                            }.addOnSuccessListener { location ->
                                val userLatLng = LatLng(location.latitude,location.longitude)
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng,18f))
                            }
                        true
                    }

                    //Location Button Layout
                    val view = mapFragment.requireView().findViewById<View>("1".toInt()).parent as View
                    val locationButton = view.findViewById<View>("2".toInt())
                    val params = locationButton?.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_TOP,0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,RelativeLayout.TRUE)
                    params.bottomMargin = 50

                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Toast.makeText(context, "Permission ${p0!!.permissionName} is denied", Toast.LENGTH_LONG).show()
                }

                override fun onPermissionRationaleShouldBeShown(p0: PermissionRequest?, p1: PermissionToken?) {}

            }).check()

        //Eternal Map Style
        try {
            val success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(context,R.raw.uber_maps_style))
            if(!success){
                Log.e("JABER_ERROR", "parsing style error")
            }

        }catch (e:Resources.NotFoundException){
            Log.e("JABER_ERROR", e.message.toString())
        }

    }

    @SuppressLint("MissingPermission")
    private fun init() {
        //Online System Init
        onlineRef = FirebaseDatabase.getInstance().reference.child(".info/connected")



        //location System Init
        locationRequest = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.fastestInterval = 3000L
        locationRequest.interval = 5000
        locationRequest.smallestDisplacement = 10f

        locationCallback = object :LocationCallback(){
            //Ctrl + o
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                val newPos = LatLng(locationResult!!.lastLocation!!.latitude, locationResult.lastLocation.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))

                val geoCoder = Geocoder(requireContext(), Locale.getDefault())
                val addressList:List<Address>

                try {
                    addressList = geoCoder.getFromLocation(locationResult.lastLocation.latitude,locationResult.lastLocation.longitude,1)
                    val cityName = addressList[0].locality

                    //Online System Init
                    driverLocationRef = FirebaseDatabase.getInstance().getReference(Common.DRIVERS_LOCATION_REFERENCE).child(cityName)
                    currentUserRef = driverLocationRef.child(currentUserUid)
                    geoFire = GeoFire(driverLocationRef)


                    //Update Driver Location
                    geoFire.setLocation(currentUserUid, GeoLocation(locationResult.lastLocation.latitude, locationResult.lastLocation.longitude)
                    ){ key:String, error:DatabaseError? ->
                        if(error != null){
                            Snackbar.make(mapFragment.requireView(),error.message,Snackbar.LENGTH_LONG).show()
                        } else
                            Snackbar.make(mapFragment.requireView(),"You're online",Snackbar.LENGTH_SHORT).show()
                    }

                    registerOnlineSystem()


                }catch (e:IOException){
                    Snackbar.make(requireView(),e.message!!,Snackbar.LENGTH_LONG).show()
                }


            }
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
        fusedLocationProviderClient.requestLocationUpdates(locationRequest,locationCallback, Looper.myLooper())

    }

    private fun registerOnlineSystem() {
        onlineRef.addValueEventListener(onlineValueEventListener)
    }

}