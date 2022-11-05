/*
 * OAndBackupX: open-source apps backup and restore app.
 * Copyright (C) 2020  Antonios Hazim
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.machiav3lli.backup.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.machiav3lli.backup.ALT_MODE_APK
import com.machiav3lli.backup.ALT_MODE_BOTH
import com.machiav3lli.backup.ALT_MODE_DATA
import com.machiav3lli.backup.ALT_MODE_UNSET
import com.machiav3lli.backup.R
import com.machiav3lli.backup.activities.MainActivityX
import com.machiav3lli.backup.dialogs.BatchDialogFragment
import com.machiav3lli.backup.fragments.AppSheet
import com.machiav3lli.backup.handler.LogsHandler
import com.machiav3lli.backup.items.Package
import com.machiav3lli.backup.ui.compose.icons.Phosphor
import com.machiav3lli.backup.ui.compose.icons.phosphor.CaretDown
import com.machiav3lli.backup.ui.compose.icons.phosphor.CircleWavyWarning
import com.machiav3lli.backup.ui.compose.item.ActionButton
import com.machiav3lli.backup.ui.compose.item.ElevatedActionButton
import com.machiav3lli.backup.ui.compose.item.ExpandingFadingVisibility
import com.machiav3lli.backup.ui.compose.recycler.HomePackageRecycler
import com.machiav3lli.backup.ui.compose.recycler.UpdatedPackageRecycler
import com.machiav3lli.backup.utils.FileUtils
import com.machiav3lli.backup.utils.StorageLocationNotConfiguredException
import com.machiav3lli.backup.utils.applyFilter
import com.machiav3lli.backup.utils.sortFilterModel
import com.machiav3lli.backup.viewmodels.HomeViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun HomePage(viewModel: HomeViewModel) {
    // TODO include tags in search
    val context = LocalContext.current
    val mainActivityX = context as MainActivityX
    var appSheet: AppSheet? = null
    val list by mainActivityX.viewModel.packageList.observeAsState(null)
    val modelSortFilter by mainActivityX.modelSortFilter.collectAsState(context.sortFilterModel)
    val filteredList by viewModel.filteredList.observeAsState(null)
    val query by mainActivityX.searchQuery.collectAsState(initial = "")
    val updatedApps = filteredList?.filter { it.isUpdated }
    var updatedVisible by remember(viewModel.filteredList.value) { mutableStateOf(false) }

    val filterPredicate = { item: Package ->
        query.isEmpty() || listOf(item.packageName, item.packageLabel)
            .find { it.contains(query, true) } != null
    }
    val queriedList = filteredList?.filter(filterPredicate)

    val batchConfirmListener = object : BatchDialogFragment.ConfirmListener {
        override fun onConfirmed(selectedPackages: List<String?>, selectedModes: List<Int>) {
            mainActivityX.startBatchAction(true, selectedPackages, selectedModes) {
                it.removeObserver(this)
            }
        }
    }

    LaunchedEffect(list, modelSortFilter) {
        try {
            viewModel.filteredList.value = list?.applyFilter(modelSortFilter, context)
        } catch (e: FileUtils.BackupLocationInAccessibleException) {
            Timber.e("Could not update application list: $e")
        } catch (e: StorageLocationNotConfiguredException) {
            Timber.e("Could not update application list: $e")
        } catch (e: Throwable) {
            LogsHandler.unhandledException(e)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            AnimatedVisibility(
                modifier = Modifier.padding(start = 26.dp),
                visible = !updatedApps.isNullOrEmpty()
            ) {
                ExpandingFadingVisibility(
                    expanded = updatedVisible,
                    expandedView = {
                        Column(
                            modifier = Modifier
                                .shadow(
                                    elevation = 6.dp,
                                    MaterialTheme.shapes.large
                                )
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.shapes.large
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    text = stringResource(id = R.string.backup_all_updated),
                                ) {
                                    val selectedList = updatedApps.orEmpty()
                                        .map { it.packageInfo }
                                        .toCollection(ArrayList())
                                    val selectedListModes = updatedApps.orEmpty()
                                        .mapNotNull {
                                            it.latestBackup?.let { bp ->
                                                when {
                                                    bp.hasApk && bp.hasAppData -> ALT_MODE_BOTH
                                                    bp.hasApk -> ALT_MODE_APK
                                                    bp.hasAppData -> ALT_MODE_DATA
                                                    else -> ALT_MODE_UNSET
                                                }
                                            }
                                        }
                                        .toCollection(ArrayList())
                                    if (selectedList.isNotEmpty()) {
                                        BatchDialogFragment(
                                            true,
                                            selectedList,
                                            selectedListModes,
                                            batchConfirmListener
                                        )
                                            .show(
                                                mainActivityX.supportFragmentManager,
                                                "DialogFragment"
                                            )
                                    }
                                }
                                ElevatedActionButton(
                                    text = "",
                                    icon = Phosphor.CaretDown,
                                    withText = false
                                ) {
                                    updatedVisible = !updatedVisible
                                }
                            }
                            UpdatedPackageRecycler(
                                productsList = updatedApps,
                                onClick = { item ->
                                    if (appSheet != null) appSheet?.dismissAllowingStateLoss()
                                    appSheet = AppSheet(item.packageName)
                                    appSheet?.showNow(
                                        mainActivityX.supportFragmentManager,
                                        "Package ${item.packageName}"
                                    )
                                }
                            )
                        }
                    },
                    collapsedView = {
                        ExtendedFloatingActionButton(onClick = {
                            updatedVisible = !updatedVisible
                        }) {
                            val text = pluralStringResource(
                                id = R.plurals.updated_apps,
                                count = updatedApps.orEmpty().size,
                                updatedApps.orEmpty().size
                            )
                            Icon(
                                imageVector = if (updatedVisible) Phosphor.CaretDown else Phosphor.CircleWavyWarning,
                                contentDescription = text
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = text)
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        HomePackageRecycler(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            productsList = queriedList ?: listOf(),
            onClick = { item ->
                if (appSheet != null) appSheet?.dismissAllowingStateLoss()
                appSheet = AppSheet(item.packageName)
                appSheet?.showNow(
                    mainActivityX.supportFragmentManager,
                    "Package ${item.packageName}"
                )
            }
        )
    }
}
