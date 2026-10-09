package com.example.msdksample.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.msdksample.R
import com.example.msdksample.databinding.FragmentAccountBinding
import com.example.msdksample.utils.PreferenceManager

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

        val prefs = PreferenceManager.getInstance(requireContext())

        // 显示用户信息（示例数据）
        binding.tvUsername.text = "巡检员"
        binding.tvPhone.text = "138****0000"

        // 修改资料按钮
        binding.btnEditProfile.setOnClickListener {
            Toast.makeText(requireContext(), "修改资料功能开发中", Toast.LENGTH_SHORT).show()
        }

        // 设备管理
        binding.llDeviceManage.setOnClickListener {
            Toast.makeText(requireContext(), "设备管理功能开发中", Toast.LENGTH_SHORT).show()
        }

        // 扫描设备
        binding.btnScanDevice.setOnClickListener {
            Toast.makeText(requireContext(), "正在扫描设备...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}