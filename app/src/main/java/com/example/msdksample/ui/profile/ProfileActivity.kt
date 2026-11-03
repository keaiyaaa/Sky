package com.example.msdksample.ui.profile

import android.text.InputFilter
import android.text.InputType
import android.util.Patterns
import com.example.msdksample.R
import com.example.msdksample.data.local.LocalDataStore
import com.example.msdksample.data.local.LocalProfile
import com.example.msdksample.ui.local.LocalToolsActivity
import com.example.msdksample.ui.local.localProfileOwner
import com.example.msdksample.ui.local.maskedPhone
import com.example.msdksample.utils.PreferenceManager

class ProfileActivity : LocalToolsActivity() {
    override fun initViews() {
        setupPage("个人资料", "资料仅保存在本机，尚未同步到账户服务。未登录时编辑的是本机演示资料。")
        val prefs = PreferenceManager.getInstance(this)
        val owner = localProfileOwner(this)
        val store = LocalDataStore.getInstance(this)
        val profile = store.profile(owner) ?: LocalProfile(if (prefs.isLoggedIn()) prefs.getUsername() else "演示用户")
        text(if (prefs.isLoggedIn()) "当前会话：${maskedPhone(prefs.getUserPhone())}" else "当前身份：本机演示")
        val nickname = field("昵称（1至20字）", profile.nickname, R.id.profile_nickname)
        nickname.filters = arrayOf(InputFilter.LengthFilter(20))
        val email = field("联系邮箱（选填）", profile.email, R.id.profile_email,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        email.filters = arrayOf(InputFilter.LengthFilter(254))
        val organization = field("所属组织（选填，最多60字）", profile.organization, R.id.profile_organization)
        organization.filters = arrayOf(InputFilter.LengthFilter(60))
        button("保存到本机") {
            val name = nickname.text.toString().trim()
            val mail = email.text.toString().trim()
            when {
                localProfileOwner(this) != owner -> { toast("登录身份已变化，请重新打开资料页面"); finish() }
                name.isBlank() -> { nickname.error = "请输入昵称"; nickname.requestFocus() }
                mail.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(mail).matches() -> {
                    email.error = "邮箱格式不正确"; email.requestFocus()
                }
                else -> runCatching {
                    check(store.saveProfile(owner, LocalProfile(name, mail, organization.text.toString().trim())))
                }.onSuccess { toast("资料已保存到本机"); finish() }
                    .onFailure { toast("保存失败，请重试") }
            }
        }
    }
}
