package org.bitanon.bitcointicker

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.bitanon.bitcointicker.databinding.FragmentBitcoinTickerBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class BitcoinTickerFragment : Fragment() {

    private var _binding: FragmentBitcoinTickerBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentBitcoinTickerBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}