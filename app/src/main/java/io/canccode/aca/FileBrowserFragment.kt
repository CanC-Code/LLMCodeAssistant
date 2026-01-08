package io.canccode.aca

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.documentfile.provider.DocumentFile
import android.widget.Toast
import io.canccode.aca.databinding.FragmentFileBrowserBinding

class FileBrowserFragment : Fragment() {

    private var _binding: FragmentFileBrowserBinding? = null
    private val binding get() = _binding!!

    private val fileListAdapter = FileListAdapter { documentFile ->
        if (documentFile.isDirectory) {
            openDirectory(documentFile.uri)
        } else {
            openFile(documentFile.uri)
        }
    }

    // SAF picker result launcher
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { loadFilesFromUri(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.fileList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileListAdapter
        }

        binding.btnPickDirectory.setOnClickListener {
            openDocumentLauncher.launch(null)
        }
    }

    private fun loadFilesFromUri(uri: Uri) {
        val pickedDir = DocumentFile.fromTreeUri(requireContext(), uri)
        if (pickedDir == null || !pickedDir.isDirectory) {
            Toast.makeText(requireContext(), "Invalid directory", Toast.LENGTH_SHORT).show()
            return
        }

        val files = pickedDir.listFiles()
        fileListAdapter.submitList(files.toList())
    }

    private fun openDirectory(uri: Uri) {
        loadFilesFromUri(uri)
    }

    private fun openFile(uri: Uri) {
        Toast.makeText(requireContext(), "File selected: $uri", Toast.LENGTH_SHORT).show()
        // TODO: send file Uri to your editor/input panel
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}