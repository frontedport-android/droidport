package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.ports.PortListScreen
import com.example.ui.ports.PortViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: PortViewModel = viewModel()
                var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }

                DisposableEffect(Unit) {
                    val authListener = FirebaseAuth.AuthStateListener { auth ->
                        currentUser = auth.currentUser
                        viewModel.onUserSignedIn(auth.currentUser)
                    }
                    Firebase.auth.addAuthStateListener(authListener)
                    onDispose {
                        Firebase.auth.removeAuthStateListener(authListener)
                    }
                }

                if (currentUser == null) {
                    AuthScreen(
                        onAuthSuccess = {
                            val user = Firebase.auth.currentUser
                            currentUser = user
                            viewModel.onUserSignedIn(user)
                        }
                    )
                } else {
                    PortListScreen(
                        viewModel = viewModel,
                        currentUser = currentUser,
                        onSignOut = {
                            Firebase.auth.signOut()
                            currentUser = null
                            viewModel.onUserSignedIn(null)
                        }
                    )
                }
            }
        }
    }
}
