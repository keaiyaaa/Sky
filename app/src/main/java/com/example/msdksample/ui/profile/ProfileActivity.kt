package com.example.msdksample.ui.profile

import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.example.msdksample.R
import com.example.msdksample.data.local.LocalDataStore
import com.example.msdksample.data.local.LocalProfile
import com.example.msdksample.ui.local.AccountCloudActivity
import com.example.msdksample.ui.local.localProfileOwner
import com.example.msdksample.ui.local.maskedPhone
import com.example.msdksample.utils.PreferenceManager
import com.example.msdksample.data.remote.*
import com.google.gson.Gson

class ProfileActivity : AccountCloudActivity() {
    private lateinit var nickname: EditText
    private lateinit var email: EditText
    private lateinit var organization: EditText
    private lateinit var owner: String
    private lateinit var baseline: LocalProfile
    private var remote: CloudProfile? = null
    private val store by lazy { LocalDataStore.getInstance(this) }
    private val json = Gson()

    override fun initViews() {
        setupPage("个人资料", "可保存到本机，或在云端登录后保存到账户。旧演示资料不会自动上传。")
        val prefs = PreferenceManager.getInstance(this)
        owner = localProfileOwner(this)
        val session = BackendSessions.registry.current()
        remote = session?.let { lease -> runCatching {
            json.fromJson(cloudStore.snapshot(lease.userId, "profile"), CloudProfile::class.java)
        }.getOrNull()?.takeIf { it.userId == lease.userId } }
        baseline = store.profile(owner) ?: LocalProfile(session?.username
            ?: if (prefs.isLoggedIn()) prefs.getUsername() else "演示用户")
        text(if (session != null) "当前云端账号：${session.username}"
            else if (prefs.isLoggedIn()) "本机会话：${maskedPhone(prefs.getUserPhone())}" else "当前身份：本机演示")
        nickname = field("昵称（1至20字）", baseline.nickname, R.id.profile_nickname)
        email = field("联系邮箱（选填，最多254字）", baseline.email, R.id.profile_email,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        organization = field("所属组织（选填，最多60字）", baseline.organization, R.id.profile_organization)
        button("保存到本机") {
            validForm()?.let { profile ->
                runCatching { check(store.saveProfile(owner, profile))
                }.onSuccess { toast("资料已保存到本机"); finish() }
                    .onFailure { toast("保存失败，请重试") }
            }
        }
        addCloudStatus()
        button("读取账户资料") {
            if (form() != baseline) AlertDialog.Builder(this).setTitle("读取账户资料")
                .setMessage("读取将替换当前输入。是否放弃当前输入并读取？")
                .setNegativeButton("保留输入", null).setPositiveButton("读取") { _, _ -> loadCloud() }.show()
            else loadCloud()
        }
        button("保存到账户") {
            val draft = validForm() ?: return@button
            val current = remote
            if (current == null) { cloudStatus.text = "请先读取账户资料，取得当前版本后再编辑保存。"; return@button }
            val saved = runCatching { store.saveProfile(owner, draft) }.getOrDefault(false)
            if (!saved) { toast("本机草稿保存失败，暂未发送"); return@button }
            cloud({ api, lease -> api.updateProfile(lease,
                ProfileUpdate(draft.nickname, draft.email, draft.organization, current.version)) }) { result, lease ->
                cloudStatus.text = if (accept(result, lease, draft)) "资料已保存到账户"
                    else "提交的资料已保存；后续输入仍在本机，尚未保存到账户。"
            }
        }
    }

    private fun loadCloud() {
        val beforeLoad = form()
        cloud({ api, lease -> api.profile(lease) }) { result, lease ->
            cloudStatus.text = if (accept(result, lease, beforeLoad)) "已读取账户资料（版本 ${result.version}）"
                else "已读取最新版本；请求期间的新输入已保留，尚未保存到账户。"
        }
    }
    private fun accept(result: CloudProfile, lease: BackendSessionRegistry.Lease, submitted: LocalProfile): Boolean {
        val currentDraft = form()
        remote = result
        baseline = LocalProfile(result.nickname, result.email, result.organization)
        cloudStore.saveSnapshot(lease.userId, "profile", json.toJson(result))
        if (currentDraft != submitted) {
            if (AccountFieldRules.validText(currentDraft.nickname, 20, true) &&
                AccountFieldRules.validText(currentDraft.email, 254) && AccountFieldRules.validEmail(currentDraft.email) &&
                AccountFieldRules.validText(currentDraft.organization, 60)) store.saveProfile("user:${lease.userId}", currentDraft)
            return false
        }
        nickname.setText(result.nickname); email.setText(result.email); organization.setText(result.organization)
        if (AccountFieldRules.validText(result.nickname, 20, true)) check(store.saveProfile("user:${lease.userId}", baseline))
        return true
    }
    private fun form() = LocalProfile(nickname.text.toString().trim(), email.text.toString().trim(), organization.text.toString().trim())
    private fun validForm(): LocalProfile? {
        if (localProfileOwner(this) != owner) { toast("登录身份已变化，请重新打开资料页面"); finish(); return null }
        nickname.error = null; email.error = null; organization.error = null
        val value = form()
        when {
            value.nickname.isBlank() -> { nickname.error = "请输入昵称"; nickname.requestFocus() }
            !AccountFieldRules.validText(value.nickname, 20, true) -> { nickname.error = "昵称最多20字，不能包含控制字符"; nickname.requestFocus() }
            !AccountFieldRules.validText(value.email, 254) || !AccountFieldRules.validEmail(value.email) -> { email.error = "邮箱格式不正确或超过254字"; email.requestFocus() }
            !AccountFieldRules.validText(value.organization, 60) -> { organization.error = "组织最多60字，不能包含控制字符"; organization.requestFocus() }
            else -> return value
        }
        return null
    }
}
