package com.example.arts.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.arts.R
import com.example.arts.utils.ImageStorageHelper
import com.example.arts.utils.PrefsHelper
import com.example.arts.utils.toast
import com.example.arts.viewmodels.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private val profileViewModel: ProfileViewModel by activityViewModels()

    // FIX: Declared as lateinit and registered in onCreate() instead of as a
    // property initializer. On Android 14+ registerForActivityResult must be
    // called before the fragment reaches STARTED state. Property initializers
    // run at construction time which is safe on first creation but throws
    // IllegalStateException when the Navigation component recreates the fragment
    // while it is already in the back stack in STARTED state.
    private lateinit var pickAvatarLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Register here — before STARTED state — safe on all Android versions.
        pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            viewLifecycleOwner.lifecycleScope.launch {
                val ctx = context ?: return@launch
                if (profileViewModel.user.value == null) {
                    toast(getString(R.string.profile_not_loaded))
                    return@launch
                }
                val dataUrl = withContext(Dispatchers.IO) {
                    ImageStorageHelper.toDataUrl(ctx, uri, maxDimension = 300, quality = 80)
                }
                if (dataUrl == null) {
                    if (isAdded) toast(getString(R.string.error_image_read))
                    return@launch
                }
                try {
                    profileViewModel.saveAvatar(dataUrl)
                    if (isAdded) {
                        profileViewModel.user.value?.let { showAvatar(dataUrl, it.fullName) }
                        toast(getString(R.string.toast_photo_updated))
                    }
                } catch (e: Exception) {
                    if (isAdded) toast(getString(R.string.error_photo_save, e.message))
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<View>(R.id.btn_back)
        val etName = view.findViewById<EditText>(R.id.et_full_name)
        val etPhone = view.findViewById<EditText>(R.id.et_phone)
        val etEmail = view.findViewById<EditText>(R.id.et_email_locked)
        val btnSave = view.findViewById<View>(R.id.btn_save_profile)
        val btnChangePhoto = view.findViewById<View>(R.id.btn_change_photo)

        btnBack?.setOnClickListener { if (isAdded) findNavController().navigateUp() }

        profileViewModel.user.observe(viewLifecycleOwner) { user ->
            user ?: return@observe
            etName?.setText(user.fullName)
            etPhone?.setText(user.phone)
            etEmail?.setText(user.email)
            showAvatar(user.avatarDataUrl, user.fullName)
        }

        btnChangePhoto?.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }

        btnSave?.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val name = etName?.text?.toString()?.trim().orEmpty()
            val phone = etPhone?.text?.toString()?.trim().orEmpty()

            if (name.isEmpty()) {
                toast(getString(R.string.enter_full_name))
                return@setOnClickListener
            }
            
            profileViewModel.updateUser(name, phone)
            toast(getString(R.string.settings_saved))
        }

        setupTheme(view)
        setupLanguage(view)
    }

    private fun showAvatar(dataUrl: String?, fullName: String = "") {
        val root = view ?: return
        val iv = root.findViewById<ImageView>(R.id.iv_avatar) ?: return
        val tvInitials = root.findViewById<TextView>(R.id.tv_avatar_initials) ?: return
        
        val bitmap = ImageStorageHelper.decodeDataUrl(dataUrl)
        if (bitmap != null) {
            iv.setImageBitmap(bitmap)
            iv.visibility = View.VISIBLE
            tvInitials.visibility = View.GONE
        } else {
            iv.visibility = View.GONE
            tvInitials.visibility = View.VISIBLE
            tvInitials.text = fullName.trim().split(" ").filter { it.isNotEmpty() }
                .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
        }
    }

    private fun setupTheme(view: View) {
        val ctx = context ?: return
        val mode = PrefsHelper.getThemeMode(ctx)
        updateThemeButtons(view, mode)

        view.findViewById<View>(R.id.btn_theme_light)?.setOnClickListener {
            toast("ARTS uses a light theme only")
        }
        view.findViewById<View>(R.id.btn_theme_dark)?.setOnClickListener {
            toast("ARTS uses a light theme only")
        }
    }

    private fun updateThemeButtons(view: View, mode: String) {
        val ctx = context ?: return
        val light = view.findViewById<Button>(R.id.btn_theme_light) ?: return
        val dark = view.findViewById<Button>(R.id.btn_theme_dark) ?: return
        
        val activeColor = ContextCompat.getColor(ctx, R.color.accent_green)
        val inactiveColor = ContextCompat.getColor(ctx, android.R.color.transparent)
        
        light.setBackgroundColor(if (mode == "light") activeColor else inactiveColor)
        dark.setBackgroundColor(if (mode == "dark") activeColor else inactiveColor)
        
        val white = ContextCompat.getColor(ctx, R.color.white)
        light.setTextColor(if (mode == "light") white else activeColor)
        dark.setTextColor(if (mode == "dark") white else activeColor)
    }

    private fun setupLanguage(view: View) {
        view.findViewById<View>(R.id.btn_lang_en)?.setOnClickListener { setLanguage("en") }
        view.findViewById<View>(R.id.btn_lang_af)?.setOnClickListener { setLanguage("af") }
        view.findViewById<View>(R.id.btn_lang_zu)?.setOnClickListener { setLanguage("zu") }
    }

    private fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        // FIX: also save to Firestore so this choice follows the user to the
        // website instead of staying stuck on just this device. A failure
        // here is logged inside the repository and never blocks the local
        // language change the user just saw happen immediately.
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                profileViewModel.saveLanguage(tag)
            } catch (e: Exception) {
                // Non-fatal — the device's language already changed regardless.
            }
        }
    }
}
