package com.labmacc.project.dsmessages

import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class FirebaseUIActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private val tag: String = "Login Activity"

    //SignIn
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { res ->
        this.onSignInResult(res)
    }

    public override fun onStart() {
        super.onStart()
        Log.i(tag, "Login activity started")
        // Check if user is signed in (non-null) and update UI accordingly.
        auth = Firebase.auth
        val currentUser = auth.currentUser
        if(currentUser != null){
            //reload();
            finish()
        }else{
            val providers = arrayListOf(
                AuthUI.IdpConfig.GoogleBuilder().build()
            )

            val signInIntent = AuthUI.getInstance().createSignInIntentBuilder().setAvailableProviders(providers).build()

            signInLauncher.launch(signInIntent)
            finish()
        }

    }

    public override fun onDestroy() {
        super.onDestroy()
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        val response = result.idpResponse
        if (result.resultCode == RESULT_OK) {
            // Successfully signed in
            val user = FirebaseAuth.getInstance().currentUser
            // ...
        } else {
            // Sign in failed. If response is null the user canceled the
            // sign-in flow using the back button. Otherwise check
            // response.getError().getErrorCode() and handle the error.
            // ...
            AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage("Unable to login, please try restarting the app")
                .setPositiveButton(R.string.ok){_,_->}
        }
    }

}