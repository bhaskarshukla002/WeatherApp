package com.example.weatherapp.activities


import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.weatherapp.R
import com.example.weatherapp.constants.Constants
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.databinding.DialogCustomProgressBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.networks.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {


    private var binding: ActivityMainBinding?=null
    private lateinit var mFusedLocationClient:FusedLocationProviderClient
    private var mProgressDialog: Dialog?=null
    private lateinit var mSharedPrefrences: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPrefrences=getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)
        setupUI()

        if(!isLocationEnabled()){
            showRationalDialogForLocation()
        }else{
            Dexter.withActivity(this)
                .withPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(object : MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if(report!!.areAllPermissionsGranted()){
                            createLocationRequest()
                        }
                        if(report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity,"",Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }

                }).onSameThread().check()
        }
    }

    private fun getLocationWeatherDetails(latitude:Double, longitude:Double){
        if(Constants.isNetworkAvailable(this)){
            val retrofit:Retrofit= Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service : WeatherService= retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall: Call<WeatherResponse> =service.getWeather(
                latitude,
                longitude,
                Constants.METRIC_UNIT,
                Constants.APP_ID
            )
            showCustomProgressDialog()
            listCall.enqueue(object : Callback<WeatherResponse>{
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    if(response!!.isSuccess){
                        hideProgressDialog()
                        val weatherList: WeatherResponse =response.body()

                        val weatherResponseJSONString =Gson().toJson(weatherList)
                        val editor=mSharedPrefrences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJSONString)
                        editor.apply()
                        setupUI()
                        Log.i("Response ","$weatherList")
                    }else{
                        val rc= response.code()
                        when(rc){
                            400->{
                                Log.i("Error 400 ","Bad connection")
                            }
                            404->{
                                Log.i("Error 404 ","Not Found")
                            }else->{
                            Log.i("Error ","Generic error")
                        }
                        }
                    }
                }

                override fun onFailure(t: Throwable?) {
                    hideProgressDialog()
                    Log.i("Errrrrrror ",t!!.message.toString())
                }

            })
        }else{
            Toast.makeText(this@MainActivity,"internet is not available",Toast.LENGTH_SHORT).show()

        }
    }
    private fun showCustomProgressDialog(){
        mProgressDialog =Dialog(this)
        mProgressDialog?.setContentView(DialogCustomProgressBinding.inflate(layoutInflater).root)
        mProgressDialog?.show()
    }
    private fun hideProgressDialog(){
        if(mProgressDialog!=null){
            mProgressDialog?.dismiss()
        }
    }

    private fun setupUI(){
        val weatherResponseJSONString= mSharedPrefrences.getString(Constants.WEATHER_RESPONSE_DATA,"")
        if(!weatherResponseJSONString.isNullOrEmpty()){
            val weatherList =Gson().fromJson(weatherResponseJSONString, WeatherResponse::class.java)
            for(i in weatherList.weather.indices){
                Log.i("Weather Name",weatherList.weather.toString())

                binding?.tvMain?.text=weatherList.weather[i].main
                binding?.tvMainDescription?.text=weatherList.weather[i].description

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    binding?.tvTemp?.text=weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                }else{
                    binding?.tvTemp?.text=weatherList.main.temp.toString() + getUnit(application.resources.configuration.toString())
                }

                binding?.tvSunriseTime?.text=unixTime(weatherList.sys.sunrise)
                binding?.tvSunsetTime?.text=unixTime(weatherList.sys.sunset)
                binding?.tvSpeed?.text=weatherList.wind.speed.toString()
                binding?.tvName?.text=weatherList.name
                binding?.tvCountry?.text=weatherList.sys.country
                binding?.tvHumidity?.text=weatherList.main.humidity.toString()+" per cent"
                binding?.tvMin?.text=weatherList.main.temp_min.toString()+" min"
                binding?.tvMax?.text=weatherList.main.temp_max.toString()+" max"

                when(weatherList.weather[i].icon){
                    "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                    "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)
                    "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                    "01n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "11n" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                }


            }
        }
    }
    private fun getUnit(value: String):String?{
        var value ="°C"
        if("US"==value||"LR"==value||"MM"==value){
            value="°F"
        }
        return value
    }
    private fun unixTime(timex:Long):String?{
        val date =  Date(timex* 1000L)
        val sdf = SimpleDateFormat("HH:mm",Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    @SuppressLint("MissingPermission")
    private fun createLocationRequest() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority= LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval=1000
        mLocationRequest.numUpdates=1

        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallBack,
            Looper.myLooper())

    }

    private val mLocationCallBack=object : LocationCallback(){
        override fun onLocationResult(p0: LocationResult) {
            super.onLocationResult(p0)
            val mLastLocation: Location? = p0?.lastLocation
            val latitude= mLastLocation?.latitude!!
            val longitude=mLastLocation?.longitude!!
            Log.e("lati: ","$latitude")
            Log.e("long: ","$longitude")
            getLocationWeatherDetails(latitude,longitude)
        }
    }

    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this).setMessage(""+
                "It looks like you have turned off permission required"+
                "for this feature. It can be under the "+
                "Applications Settings")
            .setPositiveButton("Go To Settings"){ _,_->
                try{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package",packageName,null)
                    intent.data =uri
                    startActivity(intent)
                }catch(e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){
                    dialog,_->
                dialog.dismiss()
            }.show()
    }

    private fun showRationalDialogForLocation(){
        AlertDialog.Builder(this).setMessage(""+
                "It looks like you have turned off device location which is required"+
                "for this feature. It can be swithed to on form the "+
                " Settings")
            .setPositiveButton("Go To Settings"){ _,_->
                try{
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
//                    val uri = Uri.fromParts("package",packageName,null)
//                    intent.data =uri
                    startActivity(intent)
                }catch(e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){
                    dialog,_->
                dialog.dismiss()
            }.show()
    }

    private fun isLocationEnabled():Boolean{
        val locationManager:LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    }

    override fun onDestroy() {
        super.onDestroy()
        binding=null
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return  when(item.itemId){
            R.id.action_refresh->{
                createLocationRequest()
                true
            }
            else ->return super.onOptionsItemSelected(item)
        }

    }


}