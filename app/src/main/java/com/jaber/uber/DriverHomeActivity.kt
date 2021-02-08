package com.jaber.uber

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.jaber.uber.utils.UserUtils
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.HashMap

class DriverHomeActivity : AppCompatActivity() {
    var currentUserUid = FirebaseAuth.getInstance().currentUser?.uid.toString()

    private lateinit var toolbar: Toolbar
    private lateinit var navController: NavController
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var imgAvatar: ImageView
    private lateinit var waitingDialog: AlertDialog
    private lateinit var storageReference: StorageReference
    private var imageUri: Uri? = null

    companion object {
        const val PICK_IMAGE_REQUEST = 11
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_home)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration =
            AppBarConfiguration(setOf(R.id.nav_home, R.id.nav_sign_out), drawerLayout)

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        init()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.driver_home, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    @SuppressLint("SetTextI18n")
    private fun init() {
        //init waitingDialog
        waitingDialog = AlertDialog.Builder(this)
            .setMessage("waiting ...")
            .setCancelable(false)
            .create()
        //init storageReference
        storageReference = Firebase.storage.reference

        // Drawer menu item selected Code
        navView.setNavigationItemSelectedListener {
            if (it.itemId == R.id.nav_sign_out) {
                val builder = AlertDialog.Builder(this@DriverHomeActivity)
                builder.setTitle("Sign Out")
                    .setMessage("Do you really want to sign out")
                    .setCancelable(false)
                    .setNegativeButton("CANCEL") { dialogInterface, _ -> dialogInterface.dismiss() }
                    .setPositiveButton("SIGN OUT") { _, _ ->
                        FirebaseAuth.getInstance().signOut()
                        val i = Intent(this, SplashScreenActivity::class.java)
                        i.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(i)
                        finish()
                    }

                val dialog = builder.create()
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(ContextCompat.getColor(this,android.R.color.holo_red_dark))
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(ContextCompat.getColor(this,R.color.black))
                }
                dialog.show()
            }
            true
        }

        //Retrieve data form database and put it in Nav Header
        val headerView = navView.getHeaderView(0)
        val tvName = headerView.findViewById<TextView>(R.id.tvName)
        val tvPhone = headerView.findViewById<TextView>(R.id.tvPhone)
        val tvStar = headerView.findViewById<TextView>(R.id.tvStar)
        imgAvatar = headerView.findViewById(R.id.ivAvatar)

        tvName.text = "Welcome ${Common.currentDriver!!.firstName} ${Common.currentDriver!!.lastName}"
        tvPhone.text = Common.currentDriver!!.phoneNumber
        tvStar.text = Common.currentDriver!!.rating.toString()

        // Bring Image if there is an image in storage
        if (Common.currentDriver != null && Common.currentDriver!!.avatar.isNotEmpty()) {
            Glide.with(this)
                .load(Common.currentDriver!!.avatar)
                .into(imgAvatar)
        }
        //Set an intent to choose image
        imgAvatar.setOnClickListener {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICK_IMAGE_REQUEST && data!!.data != null) {
                imageUri = data.data
                imgAvatar.setImageURI(imageUri)

                showUploadDialog()
            }
        }
    }

    private fun showUploadDialog() {
        val builder = AlertDialog.Builder(this@DriverHomeActivity)
        builder.setTitle("Change Avatar")
            .setMessage("Do you really want to change avatar?")
            .setCancelable(false)
            .setNegativeButton("CANCEL") { dialogInterface, _ -> dialogInterface.dismiss() }
            .setPositiveButton("CHANGE") { _, _ ->
                if (imageUri !=null){
                    waitingDialog.show()
                    //Create an avatar folder in Storage and subfolder
                    val avatarFolder = storageReference.child("Drivers avatars/${currentUserUid}")

                    avatarFolder.putFile(imageUri!!)
                        .addOnProgressListener {taskSnapshot->
                            val progress = (100.0*taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                            waitingDialog.setMessage(StringBuilder("uploading: ").append(progress).append("%"))
                        }
                        .addOnFailureListener {e->
                            Snackbar.make(drawerLayout,e.message.toString(),Snackbar.LENGTH_LONG).show()
                            waitingDialog.dismiss()

                        }.addOnCompleteListener { uploadTask ->
                            if(uploadTask.isSuccessful){
                                avatarFolder.downloadUrl.addOnSuccessListener { uri ->
                                    val updateData = HashMap<String,Any>()
                                    updateData["Drivers avatars"] = uri.toString()

                                    UserUtils.updateUser(drawerLayout,updateData)
                                }
                            }

                            waitingDialog.dismiss()
                        }
                }
            }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this,android.R.color.holo_red_dark))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ContextCompat.getColor(this,R.color.black))
        }
        dialog.show()
    }
}