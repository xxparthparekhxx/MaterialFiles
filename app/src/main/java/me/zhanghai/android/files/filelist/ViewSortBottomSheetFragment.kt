package me.zhanghai.android.files.filelist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filelist.FileSortOptions.By
import me.zhanghai.android.files.filelist.FileSortOptions.Order
import me.zhanghai.android.files.util.show

/**
 * A bottom sheet for changing the view type and sort options, which stays open so that several
 * options can be changed in one go.
 */
class ViewSortBottomSheetFragment : BottomSheetDialogFragment() {
    private val viewModel: FileListViewModel
        get() = (requireParentFragment() as FileListFragment).fileListViewModel

    private lateinit var viewGroup: RadioGroup
    private lateinit var sortGroup: RadioGroup
    private lateinit var ascendingSwitch: MaterialSwitch
    private lateinit var directoriesFirstSwitch: MaterialSwitch
    private lateinit var pathSpecificSwitch: MaterialSwitch

    // Guards against feeding our own updates back to the view model.
    private var isUpdating = false

    private val viewTypeIds = mapOf(
        FileViewType.LIST to View.generateViewId(),
        FileViewType.COMPACT_LIST to View.generateViewId(),
        FileViewType.GRID to View.generateViewId()
    )
    private val sortByIds = mapOf(
        By.NAME to View.generateViewId(),
        By.TYPE to View.generateViewId(),
        By.SIZE to View.generateViewId(),
        By.LAST_MODIFIED to View.generateViewId()
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = inflater.context
        val padding = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding)
        }
        root.addView(sectionTitle(R.string.view_sort_section_view))
        viewGroup = RadioGroup(context).also { group ->
            addRadio(group, viewTypeIds.getValue(FileViewType.LIST), R.string.file_list_action_view_list)
            addRadio(
                group, viewTypeIds.getValue(FileViewType.COMPACT_LIST),
                R.string.file_list_action_view_compact_list
            )
            addRadio(group, viewTypeIds.getValue(FileViewType.GRID), R.string.file_list_action_view_grid)
        }
        root.addView(viewGroup)
        root.addView(sectionTitle(R.string.view_sort_section_sort))
        sortGroup = RadioGroup(context).also { group ->
            addRadio(group, sortByIds.getValue(By.NAME), R.string.file_list_action_sort_by_name)
            addRadio(group, sortByIds.getValue(By.TYPE), R.string.file_list_action_sort_by_type)
            addRadio(group, sortByIds.getValue(By.SIZE), R.string.file_list_action_sort_by_size)
            addRadio(
                group, sortByIds.getValue(By.LAST_MODIFIED),
                R.string.file_list_action_sort_by_last_modified
            )
        }
        root.addView(sortGroup)
        ascendingSwitch = addSwitch(root, R.string.file_list_action_sort_order_ascending)
        directoriesFirstSwitch = addSwitch(root, R.string.file_list_action_sort_directories_first)
        pathSpecificSwitch = addSwitch(root, R.string.file_list_action_view_sort_path_specific)

        viewGroup.setOnCheckedChangeListener { _, checkedId ->
            if (!isUpdating) {
                viewTypeIds.entries.firstOrNull { it.value == checkedId }
                    ?.let { viewModel.viewType = it.key }
            }
        }
        sortGroup.setOnCheckedChangeListener { _, checkedId ->
            if (!isUpdating) {
                sortByIds.entries.firstOrNull { it.value == checkedId }
                    ?.let { viewModel.setSortBy(it.key) }
            }
        }
        ascendingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdating) {
                viewModel.setSortOrder(if (isChecked) Order.ASCENDING else Order.DESCENDING)
            }
        }
        directoriesFirstSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdating) {
                viewModel.setSortDirectoriesFirst(isChecked)
            }
        }
        pathSpecificSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdating) {
                viewModel.isViewSortPathSpecific = isChecked
            }
        }

        viewModel.viewTypeLiveData.observe(viewLifecycleOwner) { update() }
        viewModel.sortOptionsLiveData.observe(viewLifecycleOwner) { update() }
        viewModel.viewSortPathSpecificLiveData.observe(viewLifecycleOwner) { update() }
        return root
    }

    private fun update() {
        isUpdating = true
        viewGroup.check(viewTypeIds.getValue(viewModel.viewType))
        val sortOptions = viewModel.sortOptions
        sortGroup.check(sortByIds.getValue(sortOptions.by))
        ascendingSwitch.isChecked = sortOptions.order == Order.ASCENDING
        directoriesFirstSwitch.isChecked = sortOptions.isDirectoriesFirst
        pathSpecificSwitch.isChecked = viewModel.isViewSortPathSpecific
        isUpdating = false
    }

    private fun sectionTitle(@StringRes textRes: Int): TextView =
        TextView(requireContext()).apply {
            setText(textRes)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, paddingTop + 16, 0, paddingBottom + 8)
        }

    private fun addRadio(group: RadioGroup, id: Int, @StringRes textRes: Int) {
        group.addView(RadioButton(requireContext()).apply {
            this.id = id
            setText(textRes)
        })
    }

    private fun addSwitch(parent: LinearLayout, @StringRes textRes: Int): MaterialSwitch =
        MaterialSwitch(requireContext()).apply {
            setText(textRes)
            parent.addView(this)
        }

    companion object {
        fun show(fragment: Fragment) {
            ViewSortBottomSheetFragment().show(fragment)
        }
    }
}
