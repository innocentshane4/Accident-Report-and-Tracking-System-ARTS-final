package com.example.arts.fragments

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.arts.R
import com.example.arts.data.UserProfile
import com.example.arts.utils.BiometricHelper
import com.example.arts.utils.PrefsHelper
import com.example.arts.utils.hide
import com.example.arts.utils.show
import com.example.arts.utils.toast
import com.example.arts.viewmodels.AuthState
import com.example.arts.viewmodels.AuthViewModel

class LoginFragment : Fragment() {

    private val authViewModel: AuthViewModel by viewModels()

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var ivTogglePassword: ImageView
    private lateinit var tvForgotPassword: TextView
    private lateinit var btnSignIn: Button
    private lateinit var btnGoogle: Button
    private lateinit var btnBiometric: Button
    private lateinit var tvSignUp: TextView
    private lateinit var progressBar: ProgressBar

    private var isPasswordVisible = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_login, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        observeViewModel()
        setupClickListeners()
        resumeSavedSession()
    }

    private fun bindViews(view: View) {
        etEmail = view.findViewById(R.id.et_email)
        etPassword = view.findViewById(R.id.et_password)
        ivTogglePassword = view.findViewById(R.id.iv_toggle_password)
        tvForgotPassword = view.findViewById(R.id.tv_forgot_password)
        btnSignIn = view.findViewById(R.id.btn_sign_in)
        btnGoogle = view.findViewById(R.id.btn_google)
        btnBiometric = view.findViewById(R.id.btn_biometric)
        tvSignUp = view.findViewById(R.id.tv_sign_up)
        progressBar = view.findViewById(R.id.progress_bar)
    }

    private fun setBusy(busy: Boolean) {
        if (busy) progressBar.show() else progressBar.hide()
        btnSignIn.isEnabled = !busy
        btnGoogle.isEnabled = !busy
    }

    private fun observeViewModel() {
        authViewModel.authState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthState.Loading -> setBusy(true)
                is AuthState.Success -> {
                    setBusy(false)
                    if (isAdded) {
                        val firstName = state.user.fullName.split(" ").firstOrNull() ?: "there"
                        toast("Welcome back, $firstName!")
                        findNavController().navigate(R.id.action_login_to_home)
                    }
                    authViewModel.resetState()
                }
                is AuthState.RequiresTotp -> {
                    setBusy(false)
                    showTotpDialog(state.user)
                }
                is AuthState.Error -> {
                    setBusy(false)
                    toast(state.message)
                    authViewModel.resetState()
                }
                is AuthState.Idle -> setBusy(false)
            }
        }

        authViewModel.resetMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                toast(message)
                authViewModel.clearResetMessage()
            }
        }
    }

    private fun setupClickListeners() {
        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            etPassword.inputType = if (isPasswordVisible)
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            etPassword.setSelection(etPassword.text.length)
        }

        btnSignIn.setOnClickListener {
            authViewModel.login(etEmail.text.toString().trim(), etPassword.text.toString().trim())
        }

        btnGoogle.setOnClickListener {
            val act = activity ?: return@setOnClickListener
            authViewModel.signInWithGoogle(act)
        }

        tvForgotPassword.setOnClickListener {
            authViewModel.sendPasswordReset(etEmail.text.toString().trim())
        }

        tvSignUp.setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.action_login_to_register)
        }

        btnBiometric.setOnClickListener { askBiometricThenResume() }
    }

    private fun resumeSavedSession() {
        val uid = authViewModel.currentUid()
        val ctx = context ?: return
        if (uid == null) {
            btnBiometric.visibility = View.GONE
            return
        }

        val bioStatus = BiometricHelper.checkAvailability(this)
        val biometricOn = PrefsHelper.isBiometricEnabledFor(ctx, uid) &&
            bioStatus == BiometricHelper.Availability.AVAILABLE

        if (biometricOn) {
            btnBiometric.visibility = View.VISIBLE
            btnBiometric.text = "Unlock with biometrics"
            view?.post { if (isAdded) askBiometricThenResume() }
        } else {
            btnBiometric.visibility = View.GONE
            view?.post { if (isAdded) authViewModel.resumeSession() }
        }
    }

    private fun askBiometricThenResume() {
        BiometricHelper.authenticate(
            fragment = this,
            title = "Unlock ARTS",
            subtitle = "Use your fingerprint or face to continue",
            onSuccess = { if (isAdded) authViewModel.resumeSession() },
            onError = { message -> toast(message) }
        )
    }

    private fun showTotpDialog(user: UserProfile) {
        val ctx = context ?: return
        val input = EditText(ctx).apply {
            hint = "6-digit code"
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)

        android.app.AlertDialog.Builder(ctx)
            .setTitle("Two-Factor Authentication")
            .setMessage("Enter the 6-digit code from your authenticator app")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Verify") { _, _ ->
                authViewModel.verifyTotp(user, input.text.toString().trim())
            }
            .setNegativeButton("Cancel") { _, _ ->
                authViewModel.logout()
            }
            .show()
    }
}
