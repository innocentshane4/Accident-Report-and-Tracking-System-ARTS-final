package com.example.arts.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.arts.R
import com.example.arts.utils.BiometricHelper
import com.example.arts.utils.ImageStorageHelper
import com.example.arts.utils.PrefsHelper
import com.example.arts.utils.TotpHelper
import com.example.arts.utils.toast
import com.example.arts.viewmodels.AuthViewModel
import com.example.arts.viewmodels.ProfileViewModel

/**
 * Fragment displaying the user's profile, statistics, and access to settings/security.
 *
 * FIX: Avatar display is now consistent with SettingsFragment — when no photo is set,
 * or when the stored photo fails to decode, the user's initials are shown in the
 * avatar circle. Previously the circle was blank for new users (no photo).
 */
class ProfileFragment : Fragment() {

    private val profileViewModel: ProfileViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        profileViewModel.user.observe(viewLifecycleOwner) { user ->
            user ?: return@observe
            view.findViewById<TextView>(R.id.tv_name).text = user.fullName
            view.findViewById<TextView>(R.id.tv_email).text = user.email
            view.findViewById<TextView>(R.id.tv_phone).text = user.phone
            showAvatar(view, user.avatarDataUrl, user.fullName)
        }

        profileViewModel.totalReports.observe(viewLifecycleOwner) { count ->
            view.findViewById<TextView>(R.id.tv_reports_count).text = count.toString()
        }
        profileViewModel.resolvedReports.observe(viewLifecycleOwner) { count ->
            view.findViewById<TextView>(R.id.tv_resolved_count).text = count.toString()
        }

        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            if (isAdded) findNavController().navigateUp()
        }

        view.findViewById<CardView>(R.id.item_security).setOnClickListener {
            showSecurityOptionsDialog()
        }
        view.findViewById<CardView>(R.id.item_my_reports).setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.incidentsFragment)
        }
        view.findViewById<CardView>(R.id.item_medical_info).setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.medicalInfoFragment)
        }
        view.findViewById<CardView>(R.id.item_settings).setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.settingsFragment)
        }
        view.findViewById<CardView>(R.id.item_help).setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.helpFragment)
        }

        // FIX: Logout now has a real, visible button — the hidden long-press
        // on the name is kept as a secondary shortcut for muscle memory, but is
        // no longer the only way to log out.
        val doLogout: () -> Unit = {
            val ctx = context
            if (ctx != null) {
                android.app.AlertDialog.Builder(ctx)
                    .setTitle(R.string.logout_title)
                    .setMessage(R.string.logout_message)
                    .setPositiveButton(R.string.logout_confirm) { _, _ ->
                        authViewModel.logout()
                        if (isAdded) {
                            findNavController().navigate(
                                R.id.loginFragment,
                                null,
                                androidx.navigation.NavOptions.Builder()
                                    .setPopUpTo(R.id.nav_graph, true)
                                    .build()
                            )
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }

        view.findViewById<Button>(R.id.btn_logout).setOnClickListener { doLogout() }
        view.findViewById<TextView>(R.id.tv_name).setOnLongClickListener { doLogout(); true }
    }

    /**
     * Shows the user's photo if available, or their initials as a fallback.
     * Mirrors the behaviour of SettingsFragment.showAvatar() exactly.
     */
    private fun showAvatar(root: View, dataUrl: String?, fullName: String) {
        val iv       = root.findViewById<ImageView>(R.id.iv_profile_avatar) ?: return
        val tvInit   = root.findViewById<TextView>(R.id.tv_profile_initials) ?: return

        val bitmap = ImageStorageHelper.decodeDataUrl(dataUrl)
        if (bitmap != null) {
            iv.setImageBitmap(bitmap)
            iv.visibility    = View.VISIBLE
            tvInit.visibility = View.GONE
        } else {
            iv.visibility    = View.GONE
            tvInit.visibility = View.VISIBLE
            tvInit.text = fullName.trim()
                .split(" ")
                .filter { it.isNotEmpty() }
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .joinToString("")
        }
    }

    // -------------------------------------------------------------------------
    // Security dialogs
    // -------------------------------------------------------------------------

    private fun showSecurityOptionsDialog() {
        val ctx = context ?: return
        val options = arrayOf(getString(R.string.biometric_unlock), getString(R.string.totp_auth))
        android.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.security_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showBiometricSettingsDialog()
                    1 -> showTotpSettingsDialog()
                }
            }
            .show()
    }

    private fun showTotpSettingsDialog() {
        val ctx = context ?: return
        val currentUser = profileViewModel.user.value

        if (currentUser?.totpEnabled == true) {
            android.app.AlertDialog.Builder(ctx)
                .setTitle(R.string.totp_title)
                .setMessage(R.string.msg_2fa_on)
                .setPositiveButton(R.string.btn_turn_off) { _, _ ->
                    authViewModel.disableTotp {
                        if (isAdded) {
                            profileViewModel.loadUser()
                            toast(getString(R.string.toast_2fa_disabled))
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        val secret = TotpHelper.generateSecret()
        android.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.setup_2fa_title)
            .setMessage(getString(R.string.msg_2fa_setup, secret))
            .setPositiveButton(R.string.continue_label) { _, _ ->
                showTotpConfirmDialog(secret)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTotpConfirmDialog(secret: String) {
        val ctx = context ?: return
        val input = android.widget.EditText(ctx).apply {
            hint = getString(R.string.hint_2fa_code)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)

        android.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.title_2fa_confirm)
            .setView(input)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val code = input.text.toString().trim()
                if (TotpHelper.verifyCode(secret, code)) {
                    authViewModel.enableTotp(secret) {
                        if (isAdded) {
                            profileViewModel.loadUser()
                            toast(getString(R.string.toast_2fa_enabled))
                        }
                    }
                } else {
                    toast(getString(R.string.toast_2fa_error))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showBiometricSettingsDialog() {
        val ctx = context ?: return
        val currentlyEnabled = PrefsHelper.isBiometricEnabledFor(ctx, authViewModel.currentUid())

        if (currentlyEnabled) {
            android.app.AlertDialog.Builder(ctx)
                .setTitle(R.string.biometric_login)
                .setMessage(R.string.msg_biometric_on)
                .setPositiveButton(R.string.btn_turn_off) { _, _ ->
                    PrefsHelper.disableBiometric(ctx)
                    toast(getString(R.string.toast_biometric_disabled))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        when (BiometricHelper.checkAvailability(this)) {
            BiometricHelper.Availability.AVAILABLE -> {
                android.app.AlertDialog.Builder(ctx)
                    .setTitle(R.string.title_enable_biometric)
                    .setMessage(R.string.msg_enable_biometric)
                    .setPositiveButton(R.string.enable) { _, _ ->
                        BiometricHelper.authenticate(
                            fragment = this,
                            title = getString(R.string.title_confirm_biometric),
                            subtitle = getString(R.string.subtitle_confirm_biometric),
                            onSuccess = {
                                if (isAdded) {
                                    val uid = authViewModel.currentUid()
                                    if (uid != null) {
                                        PrefsHelper.enableBiometric(ctx, uid)
                                        toast(getString(R.string.toast_biometric_enabled))
                                    } else {
                                        toast(getString(R.string.error_uid_missing))
                                    }
                                }
                            },
                            onError = { message -> toast(message) }
                        )
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            BiometricHelper.Availability.NONE_ENROLLED -> {
                toast(getString(R.string.error_biometric_not_enrolled))
            }
            else -> {
                toast(getString(R.string.error_biometric_unsupported))
            }
        }
    }
}
