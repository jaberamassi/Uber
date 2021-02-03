package com.jaber.uber.utils

import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.jaber.uber.Common

object UserUtils {
    fun updateUser(view: View, updateData:Map<String,Any>){
        FirebaseDatabase.getInstance().getReference(Common.DRIVER_INFO_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(updateData)
            .addOnFailureListener { e->
                Snackbar.make(view,e.message.toString(),Snackbar.LENGTH_LONG).show()
            }.addOnSuccessListener {
                Snackbar.make(view,"update information success",Snackbar.LENGTH_LONG).show()

            }
    }
}