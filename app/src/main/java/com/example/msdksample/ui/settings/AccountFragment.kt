package com.example.msdksample.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.msdksample.R
import com.example.msdksample.databinding.FragmentAccountBinding
import com.example.msdksample.ui.login.LoginActivity
import com.example.msdksample.utils.PreferenceManager
import com.example.msdksample.data.local.LocalDataStore
import com.example.msdksample.ui.local.localProfileOwner
import com.example.msdksample.ui.profile.ProfileActivity
import com.example.msdksample.ui.device.DeviceManagementActivity
import com.example.msdksample.ui.logs.FlightLogActivity

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设备管理
        binding.llDeviceManage.setOnClickListener {
            startActivity(Intent(requireContext(), DeviceManagementActivity::class.java))
        }

        // 扫描设备
        binding.btnScanDevice.setOnClickListener {
            startActivity(Intent(requireContext(), DeviceManagementActivity::class.java).putExtra("scan_on_open", true))
        }
        binding.btnLocalProfile.setOnClickListener { startActivity(Intent(requireContext(), ProfileActivity::class.java)) }
        binding.btnFlightLogs.setOnClickListener { startActivity(Intent(requireContext(), FlightLogActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        refreshLoginState()
    }

    // 根据登录状态刷新用户信息与按钮
    private fun refreshLoginState() {
        val prefs = PreferenceManager.getInstance(requireContext())

        if (prefs.isLoggedIn()) {
            binding.tvUsername.text = LocalDataStore.getInstance(requireContext())
                .profile(localProfileOwner(requireContext()))?.nickname ?: prefs.getUsername()
            binding.tvPhone.text = maskPhone(prefs.getUserPhone())
            binding.btnEditProfile.text = getString(R.string.logout)
            binding.btnEditProfile.setOnClickListener {
                prefs.logout()
                Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show()
                refreshLoginState()
            }
        } else {
            binding.tvUsername.text = getString(R.string.not_logged_in)
            binding.tvPhone.text = getString(R.string.not_logged_in_hint)
            binding.btnEditProfile.text = getString(R.string.login_now)
            binding.btnEditProfile.setOnClickListener {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            }
        }
    }

    // 手机号脱敏显示，如 138****0000
    private fun maskPhone(phone: String): String {
        if (phone.length != 11) return phone
        return phone.substring(0, 3) + "****" + phone.substring(7)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
