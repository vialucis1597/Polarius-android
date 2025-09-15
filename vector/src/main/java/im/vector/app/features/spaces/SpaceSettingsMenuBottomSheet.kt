/*
 * Copyright 2021-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.spaces

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.args
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.core.extensions.setTextOrHide
import im.vector.app.core.platform.VectorBaseBottomSheetDialogFragment
import im.vector.app.core.utils.toast
import im.vector.app.databinding.BottomSheetSpaceSettingsBinding
import im.vector.app.features.analytics.plan.MobileScreen
import im.vector.app.features.home.AvatarRenderer
import im.vector.app.features.navigation.Navigator
import im.vector.app.features.rageshake.BugReporter
import im.vector.app.features.roomprofile.RoomProfileActivity
import im.vector.app.features.spaces.leave.SpaceLeaveAdvancedActivity
import im.vector.app.features.spaces.manage.ManageType
import im.vector.app.features.spaces.manage.SpaceManageActivity
import im.vector.app.features.wallet.DAOMnemonicWallet
import im.vector.app.core.di.ActiveSessionHolder
import kotlinx.parcelize.Parcelize
import org.matrix.android.sdk.api.extensions.orFalse
import org.matrix.android.sdk.api.util.toMatrixItem
import javax.inject.Inject

@Parcelize
data class SpaceBottomSheetSettingsArgs(
        val spaceId: String
) : Parcelable

@AndroidEntryPoint
class SpaceSettingsMenuBottomSheet : VectorBaseBottomSheetDialogFragment<BottomSheetSpaceSettingsBinding>() {

    @Inject lateinit var navigator: Navigator
    @Inject lateinit var avatarRenderer: AvatarRenderer
    @Inject lateinit var bugReporter: BugReporter
    @Inject lateinit var activeSessionHolder: ActiveSessionHolder

    private val spaceArgs: SpaceBottomSheetSettingsArgs by args()

    interface InteractionListener {
        fun onShareSpaceSelected(spaceId: String)
    }

    val settingsViewModel: SpaceMenuViewModel by fragmentViewModel()

    var interactionListener: InteractionListener? = null

    override val showExpanded = true

    var isLastAdmin: Boolean = false

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): BottomSheetSpaceSettingsBinding {
        return BottomSheetSpaceSettingsBinding.inflate(inflater, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analyticsScreenName = MobileScreen.ScreenName.SpaceMenu
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        views.invitePeople.views.bottomSheetActionClickableZone.debouncedClicks {
            dismiss()
            interactionListener?.onShareSpaceSelected(spaceArgs.spaceId)
        }

        views.showMemberList.views.bottomSheetActionClickableZone.debouncedClicks {
            navigator.openRoomProfile(requireContext(), spaceArgs.spaceId, RoomProfileActivity.EXTRA_DIRECT_ACCESS_ROOM_MEMBERS)
        }

        views.spaceSettings.views.bottomSheetActionClickableZone.debouncedClicks {
//            navigator.openRoomProfile(requireContext(), spaceArgs.spaceId)
            startActivity(SpaceManageActivity.newIntent(requireActivity(), spaceArgs.spaceId, ManageType.Settings))
        }

        views.daoWallet.views.bottomSheetActionClickableZone.debouncedClicks {
            showWalletDialog()
        }

        views.exploreRooms.views.bottomSheetActionClickableZone.debouncedClicks {
            startActivity(SpaceExploreActivity.newIntent(requireContext(), spaceArgs.spaceId))
        }

        views.addRooms.views.bottomSheetActionClickableZone.debouncedClicks {
            dismiss()
            startActivity(SpaceManageActivity.newIntent(requireActivity(), spaceArgs.spaceId, ManageType.AddRooms))
        }

        views.addSpaces.views.bottomSheetActionClickableZone.debouncedClicks {
            dismiss()
            startActivity(SpaceManageActivity.newIntent(requireActivity(), spaceArgs.spaceId, ManageType.AddRoomsOnlySpaces))
        }

        views.leaveSpace.views.bottomSheetActionClickableZone.debouncedClicks {
            startActivity(SpaceLeaveAdvancedActivity.newIntent(requireContext(), spaceArgs.spaceId))
        }
    }

    override fun invalidate() = withState(settingsViewModel) { state ->
        super.invalidate()

        if (state.leavingState is Success) {
            dismiss()
        }

        state.spaceSummary?.toMatrixItem()?.let {
            avatarRenderer.render(it, views.spaceAvatarImageView)
        }
        views.spaceNameView.text = state.spaceSummary?.displayName
        views.spaceDescription.setTextOrHide(state.spaceSummary?.topic?.takeIf { it.isNotEmpty() })

        views.spaceSettings.isVisible = state.canEditSettings

        views.invitePeople.isVisible = state.canInvite || state.spaceSummary?.isPublic.orFalse()
        views.addRooms.isVisible = state.canAddChild
        views.addSpaces.isVisible = state.canAddChild
    }

    private fun showWalletDialog() {
        val session = activeSessionHolder.getActiveSession()
        val wallet = DAOMnemonicWallet.getInstance(requireContext(), session)
        val roomId = spaceArgs.spaceId
        val existingWallet = wallet.getDAOWallet(roomId)

        if (existingWallet == null) {
            // 지갑이 없는 경우 - 생성/복원 옵션 표시
            showCreateWalletDialog(wallet, roomId)
        } else {
            // 지갑이 있는 경우 - 지갑 정보 표시
            showWalletInfoDialog(existingWallet, wallet)
        }
    }

    private fun showCreateWalletDialog(wallet: DAOMnemonicWallet, roomId: String) {
        val options = arrayOf("Create New Wallet", "Restore Existing Wallet")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("DAO Wallet")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> createNewWallet(wallet, roomId)
                    1 -> showRestoreWalletDialog(wallet, roomId)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createNewWallet(wallet: DAOMnemonicWallet, roomId: String) {
        try {
            val roomName = "DAO Wallet"
            val newWallet = wallet.createDAOWallet(roomId, roomName)
            
            // 지갑 생성 후 모든 DAO에 적용
            val allBalances = wallet.getAllProtocolDAOBalances()
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Wallet Created Successfully")
                .setMessage("Your DAO wallet has been created and applied to all DAOs!\n\nAddress: ${newWallet.address}\n\nMnemonic: ${newWallet.mnemonic}\n\nFound ${allBalances.size} DAOs with this wallet.\n\nPlease keep your mnemonic phrase in a safe place.")
                .setPositiveButton("Copy Mnemonic") { _, _ ->
                    copyToClipboard(newWallet.mnemonic)
                }
                .setNegativeButton("OK", null)
                .show()
        } catch (e: Exception) {
            showError("Failed to create wallet: ${e.message}")
        }
    }

    private fun showRestoreWalletDialog(wallet: DAOMnemonicWallet, roomId: String) {
        val input = android.widget.EditText(requireContext())
        input.hint = "Enter mnemonic phrase (12 words)"
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Restore Wallet")
            .setMessage("Enter your mnemonic phrase to restore your wallet:")
            .setView(input)
            .setPositiveButton("Restore") { _, _ ->
                val mnemonic = input.text.toString().trim()
                if (mnemonic.isNotEmpty()) {
                    restoreWallet(wallet, roomId, mnemonic)
                } else {
                    showError("Please enter a mnemonic phrase")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restoreWallet(wallet: DAOMnemonicWallet, roomId: String, mnemonic: String) {
        try {
            val roomName = "DAO Wallet"
            wallet.createDAOWalletFromMnemonic(roomId, roomName, "B", 1, mnemonic)
            
            // 지갑 복원 후 모든 DAO에 적용
            val allBalances = wallet.getAllProtocolDAOBalances()
            
            showSuccess("Wallet restored successfully! Found ${allBalances.size} DAOs with this wallet.")
        } catch (e: Exception) {
            showError("Failed to restore wallet: ${e.message}")
        }
    }

    private fun showWalletInfoDialog(walletData: im.vector.app.features.wallet.DAOWalletData, wallet: DAOMnemonicWallet) {
        val options = arrayOf("Copy Address", "Export Wallet", "Delete Wallet")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("DAO Wallet")
            .setMessage("Address: ${walletData.address}\nBalance: ${walletData.balance} ${walletData.currency}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> copyToClipboard(walletData.address)
                    1 -> exportWallet(walletData)
                    2 -> deleteWallet(wallet, walletData.daoId)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("DAO Wallet", text)
        clipboard.setPrimaryClip(clip)
        showSuccess("Copied to clipboard")
    }

    private fun exportWallet(walletData: im.vector.app.features.wallet.DAOWalletData) {
        val exportData = """
            DAO: ${walletData.daoId}
            Name: ${walletData.daoName}
            Mnemonic: ${walletData.mnemonic}
            Address: ${walletData.address}
            Balance: ${walletData.balance} ${walletData.currency}
            Created: ${walletData.createdAt}
        """.trimIndent()
        
        copyToClipboard(exportData)
        showSuccess("Wallet data copied to clipboard")
    }

    private fun deleteWallet(wallet: DAOMnemonicWallet, daoId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Wallet")
            .setMessage("Are you sure you want to delete your DAO wallet? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                wallet.deleteDAOWallet(daoId)
                showSuccess("Wallet deleted successfully")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showError(message: String) {
        requireContext().toast(message)
    }

    private fun showSuccess(message: String) {
        requireContext().toast(message)
    }

    companion object {
        fun newInstance(spaceId: String, interactionListener: InteractionListener): SpaceSettingsMenuBottomSheet {
            return SpaceSettingsMenuBottomSheet().apply {
                this.interactionListener = interactionListener
                setArguments(SpaceBottomSheetSettingsArgs(spaceId))
            }
        }
    }
}
