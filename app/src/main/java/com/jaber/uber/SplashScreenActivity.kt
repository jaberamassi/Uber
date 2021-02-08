package com.jaber.uber

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.jaber.uber.model.DriverInfoModel
import com.jaber.uber.databinding.ActivitySplashScreenBinding
import com.jaber.uber.utils.UserUtils


class SplashScreenActivity : AppCompatActivity() {
    companion object {
        const val RC_SIGN_IN = 7171
    }

    private lateinit var binding: ActivitySplashScreenBinding

    private lateinit var auth: FirebaseAuth
    private lateinit var providers: List<AuthUI.IdpConfig>
    private lateinit var listener: FirebaseAuth.AuthStateListener

    private lateinit var database: FirebaseDatabase
    private lateinit var driverInfoRef: DatabaseReference

    override fun onStart() {
        super.onStart()
        delaySplashScreen()
    }

    override fun onStop() {
        if (auth != null && listener != null) auth.removeAuthStateListener(listener)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        auth = Firebase.auth
        database = Firebase.database
        driverInfoRef = database.getReference(Common.DRIVER_INFO_REFERENCE)

        init()


    }

    private fun delaySplashScreen() {

        Handler(Looper.getMainLooper()).postDelayed({ auth.addAuthStateListener(listener) }, 3000)

    }

    private fun init() {
        providers = arrayListOf(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build(),
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.TwitterBuilder().build()

        )

        listener = FirebaseAuth.AuthStateListener { myAuth ->
            val user = myAuth.currentUser
            if (user != null) {
                Log.i("AuthStateListener" , user.uid)

                // Lecture #9 Retrieve the current registration token
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w("TOKEN", "Fetching FCM registration token failed", task.exception)
                        return@addOnCompleteListener
                    }

                    // Get new FCM registration token
                    val token = task.result
                    Log.d("TOKEN", token)
                    UserUtils.updateToken(this@SplashScreenActivity, token)
                }

                checkUserFromFirebase()

            } else {
                showLoginLayout()
            }
        }
    }

    private fun checkUserFromFirebase() {
        driverInfoRef.child(auth.currentUser!!.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
//                        Toast.makeText(this@SplashScreenActivity, "user already register!!", Toast.LENGTH_LONG).show()

                        val driver = snapshot.getValue(DriverInfoModel::class.java)
                        Log.i("driver" , driver?.firstName.toString())
                        Toast.makeText(this@SplashScreenActivity, "welcome: ${driver?.firstName}", Toast.LENGTH_LONG).show()
                        goToHomeActivity(driver)

                        binding.progressBar.visibility = View.GONE
                    } else {
                        showRegisterLayout()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w("DatabaseError", "loadPost:onCancelled", error.toException())
                    Toast.makeText(this@SplashScreenActivity, error.message, Toast.LENGTH_LONG).show()
                }

            })
    }

    private fun goToHomeActivity(driver: DriverInfoModel?) {
        Common.currentDriver = driver // to send driver Info to Common Object <== Very Important
        startActivity(Intent(this,DriverHomeActivity::class.java))
        finish()
    }

    private fun showRegisterLayout() {
        val builder = AlertDialog.Builder(this, R.style.dialogTheme)
        val itemView = LayoutInflater.from(this).inflate(R.layout.layout_register, null)

        val etFirstName = itemView.findViewById<EditText>(R.id.etFirstName)
        val etLastName = itemView.findViewById<EditText>(R.id.etLastName)
        val etPhone = itemView.findViewById<EditText>(R.id.etPhone)

        val btnContinue = itemView.findViewById<Button>(R.id.btnRegister)

        //Set Data
        if (FirebaseAuth.getInstance().currentUser?.phoneNumber != null && !TextUtils.isDigitsOnly(
                FirebaseAuth.getInstance().currentUser?.phoneNumber)
        ) {
            etPhone.setText(FirebaseAuth.getInstance().currentUser?.phoneNumber)
        }

        //Set View
        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()

        //Event
        btnContinue.setOnClickListener {
            when {
                etFirstName.text.toString().isEmpty() -> {
                    Toast.makeText(this, "Please enter First Name", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                etLastName.text.toString().isEmpty() -> {
                    Toast.makeText(this, "Please enter Last Name", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                etPhone.text.toString().isEmpty() -> {
                    Toast.makeText(this, "Please enter Phone Number", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                else -> {
                    val driver = DriverInfoModel()

                    driver.firstName = etFirstName.text.toString()
                    driver.lastName = etLastName.text.toString()
                    driver.phoneNumber = etPhone.text.toString()

                    //Upload to firebase database
                    driverInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                        .setValue(driver)
                        .addOnFailureListener { exception ->
                            Toast.makeText(this, exception.message, Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                            binding.progressBar.visibility = View.GONE
                        }.addOnSuccessListener {
                            Toast.makeText(this, "Register Success", Toast.LENGTH_LONG).show()
                            dialog.dismiss()

                            goToHomeActivity(driver)
                            binding.progressBar.visibility = View.GONE
                        }
                }
            }
        }
    }

    private fun showLoginLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
            .setPhoneButtonId(R.id.btnPhoneSignIn)
            .setGoogleButtonId(R.id.btnGoogleSignIn)
            .setEmailButtonId(R.id.btnEmailSignIn)
            .setTwitterButtonId(R.id.btnTwitterSignIn)
            .build()

        startActivityForResult(
            AuthUI.getInstance().createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setAvailableProviders(providers)
                .setTheme(R.style.loginTheme).setIsSmartLockEnabled(false).build(),
            RC_SIGN_IN
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            if (resultCode == Activity.RESULT_OK) {
                // Successfully signed in
                val user = auth.currentUser
            } else {
                Toast.makeText(this, response?.error?.message, Toast.LENGTH_LONG).show()
            }
        }
    }

}

